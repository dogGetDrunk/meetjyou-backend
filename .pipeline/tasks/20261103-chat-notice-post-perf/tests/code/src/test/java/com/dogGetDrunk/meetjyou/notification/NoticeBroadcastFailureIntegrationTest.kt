package com.dogGetDrunk.meetjyou.notification

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.notification.event.NoticeBroadcastEventHandler
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
import io.kotest.matchers.string.shouldContain
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class NoticeFailureStatementInspector : StatementInspector {
    override fun inspect(sql: String): String {
        if (armed.get() == true && sql.trimStart().lowercase().startsWith("insert into notification_outbox")) {
            val next = count.get() + 1
            count.set(next)
            if (next == 2) throw IllegalStateException("injected second outbox insert failure")
        }
        return sql
    }

    companion object {
        val armed = ThreadLocal.withInitial { false }
        val count = ThreadLocal.withInitial { 0 }
    }
}

@SpringBootTest(properties = [
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=" +
        "com.dogGetDrunk.meetjyou.notification.NoticeFailureStatementInspector",
])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@MockBean(FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class)
class NoticeBroadcastFailureIntegrationTest : FunSpec() {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwtProvider: JwtProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @MockBean private lateinit var discord: DiscordAlertService

    init {
        extensions(SpringExtension())

        test("AT-004-1 API commits notices while second outbox insert failure rolls back all rows") {
            val admin = saveUser(Role.ADMIN)
            saveUser(Role.USER)
            saveUser(Role.USER)
            val token = jwtProvider.generateAccessToken(admin.uuid, Role.ADMIN)
            val logger = LoggerFactory.getLogger(NoticeBroadcastEventHandler::class.java) as Logger
            val appender = ListAppender<ILoggingEvent>()
            appender.start()
            logger.addAppender(appender)
            try {
                val first = publishWithFailure(token, appender)
                val second = publishWithFailure(token, appender)
                (first == second) shouldBe false
            } finally {
                logger.detachAppender(appender)
                appender.stop()
                NoticeFailureStatementInspector.armed.remove()
                NoticeFailureStatementInspector.count.remove()
            }
        }
    }

    private fun saveUser(role: Role): User {
        val suffix = UUID.randomUUID().toString().take(8)
        return users.saveAndFlush(User(null, "n$suffix", AuthProvider.KAKAO, suffix, role))
    }

    private fun publishWithFailure(token: String, appender: ListAppender<ILoggingEvent>): UUID {
        Mockito.clearInvocations(discord)
        appender.list.clear()
        NoticeFailureStatementInspector.count.set(0)
        NoticeFailureStatementInspector.armed.set(true)
        val response = try {
            mvc.perform(
                post("/api/v1/notices")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"failure test","body":"body","notify":true}"""),
            ).andReturn().response
        } finally {
            NoticeFailureStatementInspector.armed.set(false)
        }
        response.status shouldBe 200
        NoticeFailureStatementInspector.count.get() shouldBe 2
        val uuid = UUID.fromString(mapper.readTree(response.contentAsString).get("uuid").asText())
        assertCommittedState(uuid)
        assertFailureReported(uuid, appender)
        return uuid
    }

    private fun assertCommittedState(uuid: UUID) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            jdbc.queryForObject("select count(*) from notice where uuid = ?", Long::class.java, uuid.toString()) shouldBe 1L
            jdbc.queryForObject(
                "select count(*) from notification_outbox where dedup_key like ?",
                Long::class.java,
                "notice:$uuid:%",
            ) shouldBe 0L
        }
    }

    private fun assertFailureReported(uuid: UUID, appender: ListAppender<ILoggingEvent>) {
        val errors = appender.list.filter { it.level == Level.ERROR }
        errors.size shouldBe 1
        val calls = Mockito.mockingDetails(discord).invocations.filter { it.method.name == "sendOperationalAlert" }
        calls.size shouldBe 1
        val arguments = calls.single().arguments
        arguments[0].toString() shouldBe "NoticeBroadcast:$uuid"
        val exceptionClass = arguments[1].toString()
        exceptionClass.isNotBlank() shouldBe true
        errors.single().throwableProxy.className.substringAfterLast('.') shouldBe exceptionClass
        arguments[2].toString() shouldContain "0 sent"
        arguments[2].toString() shouldContain "POST /api/v1/notices/$uuid/broadcast"
    }
}
