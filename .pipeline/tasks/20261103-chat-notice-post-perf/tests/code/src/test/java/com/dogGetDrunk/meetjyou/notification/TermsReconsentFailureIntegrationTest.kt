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
import com.dogGetDrunk.meetjyou.terms.dto.PublishTermsRequest
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

class TermsOutboxFailureInspector : StatementInspector {
    override fun inspect(sql: String): String {
        val count = insertCount.get() ?: return sql
        if (sql.trimStart().lowercase().startsWith("insert into notification_outbox")) {
            val next = count + 1
            insertCount.set(next)
            if (next == FAIL_ON_INSERT) throw InjectedTermsOutboxFailure()
        }
        return sql
    }

    companion object {
        private const val FAIL_ON_INSERT = 2
        private val insertCount = ThreadLocal<Int>()
        fun arm() = insertCount.set(0)
        fun observedInserts(): Int = insertCount.get() ?: 0
        fun disarm() = insertCount.remove()
    }
}

class InjectedTermsOutboxFailure : RuntimeException("injected second outbox insert failure")

@SpringBootTest(
    properties = [
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.dogGetDrunk.meetjyou.notification.TermsOutboxFailureInspector",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
    NotificationDispatcher::class,
)
class TermsReconsentFailureIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jwtProvider: JwtProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var termsRepository: TermsRepository
    @Autowired private lateinit var userTermsRepository: UserTermsRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @MockBean private lateinit var contentUrlGenerator: TermsContentUrlGenerator
    @MockBean private lateinit var discord: DiscordAlertService

    private val createdUserIds = mutableListOf<Long>()
    private val createdTermsUuids = mutableListOf<UUID>()
    private val handlerLogger = LoggerFactory.getLogger(TermsReconsentEventHandler::class.java) as Logger
    private val logs = ListAppender<ILoggingEvent>()

    init {
        extensions(SpringExtension())
        beforeEach {
            Mockito.`when`(contentUrlGenerator.verifyContent(anyString(), anyString())).thenReturn(true)
            Mockito.clearInvocations(discord)
            logs.start()
            handlerLogger.addAppender(logs)
        }
        afterEach {
            TermsOutboxFailureInspector.disarm()
            handlerLogger.detachAppender(logs)
            logs.stop()
            cleanup()
            logs.list.clear()
        }

        given("AT-004-2: previously agreed active terms and two eligible users") {
            `when`("two different versions fail on their second outbox insert after publication") {
                then("both publications commit while each broadcast rolls back and alerts once") {
                    val fixture = createFixture()
                    val first = publishAndVerify(fixture.token, fixture.previousUuid, "2.0")
                    val second = publishAndVerify(fixture.token, first.uuid, "3.0")
                    first.uuid shouldNotBe second.uuid
                    first.source shouldNotBe second.source
                    first.exceptionClass shouldBe second.exceptionClass
                }
            }
        }
    }

    private fun createFixture(): Fixture {
        val runId = UUID.randomUUID().toString().substring(0, 8)
        val tx = TransactionTemplate(transactionManager)
        return tx.execute {
            val admin = saveUser("a$runId", Role.ADMIN)
            val first = saveUser("b$runId", Role.USER)
            val second = saveUser("c$runId", Role.USER)
            val previous = termsRepository.save(
                Terms(
                    type = TermsType.TERMS_OF_SERVICE,
                    version = "1.0",
                    displayText = "Service terms",
                    required = true,
                    contentObjectKey = "terms/service/v1.0.html",
                    contentHash = "hash",
                    effectiveAt = Instant.now().minusSeconds(3600),
                ),
            )
            createdTermsUuids.add(previous.uuid)
            userTermsRepository.saveAll(listOf(UserTerms(previous, first), UserTerms(previous, second)))
            Fixture(previous.uuid, jwtProvider.generateAccessToken(admin.uuid, Role.ADMIN))
        } ?: error("Fixture transaction failed")
    }

    private fun saveUser(nickname: String, role: Role): User {
        val user = userRepository.save(
            User(null, nickname, AuthProvider.KAKAO, "terms-failure-$nickname", role),
        )
        createdUserIds.add(user.id)
        return user
    }

    private fun publishAndVerify(token: String, previousUuid: UUID, version: String): FailureResult {
        val beforeErrors = errors().size
        val beforeAlerts = alerts().size
        val body = PublishTermsRequest(
            TermsType.TERMS_OF_SERVICE, version, "Service terms", true, "hash", Instant.now().minusSeconds(3600),
        )
        TermsOutboxFailureInspector.arm()
        var attempts = 0
        val response = try {
            mockMvc.perform(
                post("/api/v1/terms")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)),
            ).andExpect(status().isOk).andReturn().response
        } finally {
            attempts = TermsOutboxFailureInspector.observedInserts()
            TermsOutboxFailureInspector.disarm()
        }
        attempts shouldBe 2
        val json = objectMapper.readTree(response.contentAsString)
        val uuid = UUID.fromString(json.path("termsUuid").asText())
        createdTermsUuids.add(uuid)
        json.path("version").asText() shouldBe version
        verifyCommittedState(previousUuid, uuid, version)
        return verifyFailure(uuid, beforeErrors, beforeAlerts)
    }

    private fun verifyCommittedState(previousUuid: UUID, uuid: UUID, version: String) {
        val tx = TransactionTemplate(transactionManager)
        tx.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        tx.executeWithoutResult {
            val previous = termsRepository.findByUuid(previousUuid) ?: error("Previous terms missing")
            val published = termsRepository.findByUuid(uuid) ?: error("Published terms missing")
            previous.status shouldBe TermsStatus.INACTIVE
            published.status shouldBe TermsStatus.ACTIVE
            published.version shouldBe version
            jdbcTemplate.queryForObject(
                "select count(*) from notification_outbox where dedup_key like ?", Long::class.java,
                "terms_reconsent:$uuid:%",
            ) shouldBe 0L
        }
    }

    private fun verifyFailure(uuid: UUID, beforeErrors: Int, beforeAlerts: Int): FailureResult {
        val newErrors = errors().drop(beforeErrors)
        newErrors.size shouldBe 1
        val newAlerts = alerts().drop(beforeAlerts)
        newAlerts.size shouldBe 1
        val args = newAlerts.single().arguments
        val source = args[0] as String
        val exceptionClass = args[1] as String
        val summary = args[2] as String
        source shouldBe "TermsReconsentBroadcast:$uuid"
        exceptionClass.isNotBlank() shouldBe true
        newErrors.single().throwableProxy.className.substringAfterLast('.') shouldBe exceptionClass
        summary shouldContain "0 sent"
        summary shouldContain "POST /api/v1/terms/$uuid/reconsent-broadcast"
        return FailureResult(uuid, source, exceptionClass)
    }

    private fun errors(): List<ILoggingEvent> = logs.list.filter { it.level == Level.ERROR }

    private fun alerts() = Mockito.mockingDetails(discord).invocations
        .filter { it.method.name == "sendOperationalAlert" }

    private fun cleanup() {
        val tx = TransactionTemplate(transactionManager)
        tx.executeWithoutResult {
            createdTermsUuids.forEach { uuid ->
                jdbcTemplate.update("delete from notification_outbox where dedup_key like ?", "terms_reconsent:$uuid:%")
            }
            createdUserIds.forEach { id -> jdbcTemplate.update("delete from user_terms where user_id = ?", id) }
            createdTermsUuids.forEach { uuid -> termsRepository.findByUuid(uuid)?.let(termsRepository::delete) }
            createdUserIds.forEach { id -> userRepository.deleteById(id) }
        }
        createdTermsUuids.clear()
        createdUserIds.clear()
    }

    private data class Fixture(val previousUuid: UUID, val token: String)
    private data class FailureResult(val uuid: UUID, val source: String, val exceptionClass: String)
}
