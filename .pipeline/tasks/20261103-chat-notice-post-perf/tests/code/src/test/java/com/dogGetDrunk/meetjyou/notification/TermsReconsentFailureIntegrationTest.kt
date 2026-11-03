package com.dogGetDrunk.meetjyou.notification

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.notification.dispatcher.NotificationDispatcher
import com.dogGetDrunk.meetjyou.notification.event.TermsReconsentEventHandler
import com.dogGetDrunk.meetjyou.terms.Terms
import com.dogGetDrunk.meetjyou.terms.TermsContentUrlGenerator
import com.dogGetDrunk.meetjyou.terms.TermsRepository
import com.dogGetDrunk.meetjyou.terms.TermsStatus
import com.dogGetDrunk.meetjyou.terms.TermsType
import com.dogGetDrunk.meetjyou.terms.UserTerms
import com.dogGetDrunk.meetjyou.terms.UserTermsRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class TermsOutboxInsertFailureInspector : StatementInspector {
    override fun inspect(sql: String): String {
        val state = armed.get() ?: return sql
        if (sql.trimStart().lowercase().startsWith("insert into notification_outbox")) {
            state.count++
            if (state.count == 2) throw InjectedOutboxFailure()
        }
        return sql
    }

    class State(var count: Int = 0)
    class InjectedOutboxFailure : RuntimeException("second outbox insert")

    companion object {
        val armed = ThreadLocal<State>()
    }
}

@SpringBootTest(properties = [
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.dogGetDrunk.meetjyou.notification.TermsOutboxInsertFailureInspector",
])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@MockBean(FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class)
class TermsReconsentFailureIntegrationTest : FunSpec() {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var terms: TermsRepository
    @Autowired private lateinit var userTerms: UserTermsRepository
    @Autowired private lateinit var transactions: TransactionTemplate
    @Autowired private lateinit var jdbc: JdbcTemplate
    @MockBean private lateinit var contentGenerator: TermsContentUrlGenerator
    @MockBean private lateinit var alerts: DiscordAlertService
    @MockBean private lateinit var dispatcher: NotificationDispatcher

    init {
        extensions(SpringExtension())

        test("AT-004-2 publish commits while every outbox insert rolls back and failures alert separately") {
            `when`(contentGenerator.verifyContent(anyString(), anyString())).thenReturn(true)
            val logger = LoggerFactory.getLogger(TermsReconsentEventHandler::class.java) as Logger
            val appender = ListAppender<ILoggingEvent>()
            appender.start()
            logger.addAppender(appender)
            try {
                val admin = user("admin", Role.ADMIN)
                val previous = terms.save(Terms(TermsType.TERMS_OF_SERVICE, "1", "Terms", true, "old", "hash"))
                val recipients = listOf(user("user1"), user("user2"))
                userTerms.saveAll(recipients.map { UserTerms(previous, it) })
                val first = publish(admin, "2")
                verifyCommitted(previous.uuid, first, "2")
                val second = publish(admin, "3")
                verifyCommitted(first, second, "3")
                (first == second) shouldBe false
                val errors = appender.list.filter { it.level == Level.ERROR }
                errors.size shouldBe 2
                listOf(first, second).forEach { uuid ->
                    errors.count { it.formattedMessage.contains(uuid.toString()) } shouldBe 1
                }
                val calls = mockingDetails(alerts).invocations.filter { it.method.name == "sendOperationalAlert" }
                calls.size shouldBe 2
                val sources = calls.map { it.arguments[0] as String }
                (sources[0] == sources[1]) shouldBe false
                val exceptionClass = calls[0].arguments[1] as String
                exceptionClass.isNotBlank() shouldBe true
                listOf(first, second).forEachIndexed { index, uuid ->
                    sources[index].contains(uuid.toString()) shouldBe true
                    calls[index].arguments[1] shouldBe exceptionClass
                    errors[index].throwableProxy.className.substringAfterLast('.') shouldBe exceptionClass
                    val summary = calls[index].arguments[2] as String
                    summary.contains("0 sent") shouldBe true
                    summary.contains("POST /api/v1/terms/$uuid/reconsent-broadcast") shouldBe true
                }
            } finally {
                logger.detachAppender(appender)
                appender.stop()
                TermsOutboxInsertFailureInspector.armed.remove()
            }
        }
    }

    private fun user(name: String, role: Role = Role.USER): User = users.save(
        User(null, name, AuthProvider.KAKAO, "terms-failure-$name", role),
    )

    private fun publish(admin: User, version: String): UUID {
        val state = TermsOutboxInsertFailureInspector.State()
        TermsOutboxInsertFailureInspector.armed.set(state)
        val result = try {
            mvc.perform(
                post("/api/v1/terms")
                    .header("Authorization", "Bearer ${jwt.generateAccessToken(admin.uuid, Role.ADMIN)}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"TERMS_OF_SERVICE","version":"$version","displayText":"Terms","required":true,"contentHash":"hash"}"""),
            ).andExpect(status().isOk).andReturn()
        } finally {
            TermsOutboxInsertFailureInspector.armed.remove()
        }
        state.count shouldBe 2
        val response = mapper.readTree(result.response.contentAsString)
        response["version"].asText() shouldBe version
        return UUID.fromString(response["termsUuid"].asText())
    }

    private fun verifyCommitted(previous: UUID, published: UUID, version: String) {
        transactions.executeWithoutResult {
            terms.findByUuid(previous)?.status shouldBe TermsStatus.INACTIVE
            val newTerms = terms.findByUuid(published)
            newTerms?.status shouldBe TermsStatus.ACTIVE
            newTerms?.version shouldBe version
            val count = jdbc.queryForObject(
                "select count(*) from notification_outbox where dedup_key like ?", Long::class.java,
                "terms_reconsent:$published:%",
            )
            count shouldBe 0L
        }
    }
}
