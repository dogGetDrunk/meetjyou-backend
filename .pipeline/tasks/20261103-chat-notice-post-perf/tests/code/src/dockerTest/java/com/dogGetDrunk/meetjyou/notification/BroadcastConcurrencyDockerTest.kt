package com.dogGetDrunk.meetjyou.notification

import com.dogGetDrunk.meetjyou.notice.Notice
import com.dogGetDrunk.meetjyou.notice.NoticeRepository
import com.dogGetDrunk.meetjyou.support.SqlPausePoint
import com.dogGetDrunk.meetjyou.terms.Terms
import com.dogGetDrunk.meetjyou.terms.TermsContentUrlGenerator
import com.dogGetDrunk.meetjyou.terms.TermsRepository
import com.dogGetDrunk.meetjyou.terms.TermsStatus
import com.dogGetDrunk.meetjyou.terms.TermsType
import com.dogGetDrunk.meetjyou.terms.UserTerms
import com.dogGetDrunk.meetjyou.terms.UserTermsRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
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
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest(properties = ["spring.config.additional-location=file:./src/test/resources/", "spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@MockBean(FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class)
class BroadcastConcurrencyDockerTest : BehaviorSpec() {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var notices: NoticeRepository
    @Autowired private lateinit var terms: TermsRepository
    @Autowired private lateinit var userTerms: UserTermsRepository
    @MockBean private lateinit var content: TermsContentUrlGenerator

    private data class Response(val status: Int, val body: String)
    private data class Fixture(val recipients: Set<String>, val sourceId: Long, val uuid: UUID)
    private val tx by lazy { TransactionTemplate(transactionManager) }

    private fun seed(isTerms: Boolean, forOriginal: Boolean): Fixture = tx.execute {
        val recipients = (1..2).map {
            val tag = UUID.randomUUID().toString().take(8)
            users.save(User("$tag@example.com", "u$tag", AuthProvider.KAKAO, tag))
        }
        if (!isTerms) {
            val source = notices.save(Notice("seed", "body"))
            Fixture(recipients.map { it.uuid.toString() }.toSet(), source.id, source.uuid)
        } else {
            val old = terms.save(newTerms("old", if (forOriginal) TermsStatus.ACTIVE else TermsStatus.INACTIVE))
            recipients.forEach { userTerms.save(UserTerms(old, it)) }
            val source = if (forOriginal) old else terms.save(newTerms("new", TermsStatus.ACTIVE))
            Fixture(recipients.map { it.uuid.toString() }.toSet(), source.id, source.uuid)
        }
    } ?: error("fixture transaction failed")

    private fun newTerms(version: String, status: TermsStatus) = Terms(
        TermsType.TERMS_OF_SERVICE, version, "terms", true, "terms/$version", "hash", status,
        Instant.now().minusSeconds(60),
    )

    private fun request(path: String, body: String = "{}"): Response {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin", "unused", listOf(SimpleGrantedAuthority("ADMIN")),
        )
        return try {
            val response = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().response
            Response(response.status, response.contentAsString)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private data class Scenario(val isTerms: Boolean, val original: Boolean, val fixture: Fixture, val marker: String) {
        val table get() = if (isTerms) "terms" else "notice"
        val prefix get() = if (isTerms) "terms_reconsent" else "notice"
    }

    // The first request pauses right before its outbox insert while holding the source row lock,
    // so the second request must be seen waiting on that lock before the first is released.
    private fun race(isTerms: Boolean, original: Boolean) {
        val scenario = Scenario(isTerms, original, seed(isTerms, original), UUID.randomUUID().toString())
        val point = SqlPausePoint.Point("insert into notification_outbox")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<Response> { pausedFirstRequest(scenario, point) }
            point.reached.await(TIMEOUT, TimeUnit.SECONDS) shouldBe true
            val source = resolveSource(scenario)
            outboxKeys(scenario.prefix, source.second).size shouldBe 0
            val second = executor.submit<Response> { request(resendPath(isTerms, source.second)) }
            awaitSourceWait(scenario.table, source.first) shouldBe true
            point.release.countDown()
            assertResponses(scenario, first.get(TIMEOUT, TimeUnit.SECONDS), second.get(TIMEOUT, TimeUnit.SECONDS))
            assertOneCompleteSet(scenario, source)
        } finally {
            point.release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(TIMEOUT, TimeUnit.SECONDS) shouldBe true
        }
    }

    private fun pausedFirstRequest(scenario: Scenario, point: SqlPausePoint.Point): Response {
        SqlPausePoint.arm(point)
        return try {
            if (scenario.original) {
                request(originalPath(scenario.isTerms), originalBody(scenario))
            } else {
                request(resendPath(scenario.isTerms, scenario.fixture.uuid))
            }
        } finally {
            SqlPausePoint.disarm()
        }
    }

    private fun originalPath(isTerms: Boolean) = if (isTerms) "/api/v1/terms" else "/api/v1/notices"

    private fun originalBody(scenario: Scenario) = if (scenario.isTerms) {
        """{"type":"TERMS_OF_SERVICE","version":"new","displayText":"${scenario.marker}","required":true,"contentHash":"hash"}"""
    } else {
        """{"title":"${scenario.marker}","body":"body","notify":true,"critical":false}"""
    }

    private fun resolveSource(scenario: Scenario): Pair<Long, UUID> = if (scenario.original) {
        committedSource(scenario.isTerms, scenario.marker)
    } else {
        scenario.fixture.sourceId to scenario.fixture.uuid
    }

    private fun assertResponses(scenario: Scenario, firstResponse: Response, secondResponse: Response) {
        firstResponse.status shouldBe 200
        if (!scenario.original) {
            mapper.readTree(firstResponse.body)["recipientCount"].asInt() shouldBe scenario.fixture.recipients.size
        }
        secondResponse.status shouldBe 409
        val errorCode = mapper.readTree(secondResponse.body)["errorCode"].asText()
        (errorCode in setOf("BROADCAST_ALREADY_SENT", "CONCURRENT_REQUEST_CONFLICT")) shouldBe true
    }

    private fun assertOneCompleteSet(scenario: Scenario, source: Pair<Long, UUID>) {
        val keys = outboxKeys(scenario.prefix, source.second)
        keys.size shouldBe scenario.fixture.recipients.size
        keys.toSet() shouldBe scenario.fixture.recipients.map { "${scenario.prefix}:${source.second}:$it" }.toSet()
        jdbc.queryForObject("SELECT COUNT(*) FROM ${scenario.table} WHERE id = ?", Int::class.java, source.first) shouldBe 1
    }

    private fun resendPath(isTerms: Boolean, uuid: UUID) = if (isTerms) {
        "/api/v1/terms/$uuid/reconsent-broadcast"
    } else {
        "/api/v1/notices/$uuid/broadcast"
    }

    private fun committedSource(isTerms: Boolean, marker: String): Pair<Long, UUID> {
        val sql = if (isTerms) "SELECT id, uuid FROM terms WHERE display_text = ?" else "SELECT id, uuid FROM notice WHERE title = ?"
        return jdbc.queryForObject(sql, { rs, _ -> rs.getLong(1) to UUID.fromString(rs.getString(2)) }, marker)
            ?: error("committed source missing")
    }

    private fun outboxKeys(prefix: String, uuid: UUID): List<String> = tx.execute {
        jdbc.queryForList("SELECT dedup_key FROM notification_outbox WHERE dedup_key LIKE ?", String::class.java, "$prefix:$uuid:%")
    } ?: error("outbox query failed")

    private fun awaitSourceWait(table: String, id: Long): Boolean = DriverManager.getConnection(mysql.jdbcUrl, "root", mysql.password).use connectionUse@ { connection ->
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        val sql = """SELECT COUNT(*) FROM performance_schema.data_lock_waits w
            JOIN performance_schema.data_locks blocked ON blocked.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
            JOIN performance_schema.data_locks held ON held.ENGINE_TRANSACTION_ID = w.BLOCKING_ENGINE_TRANSACTION_ID
            WHERE blocked.OBJECT_SCHEMA = DATABASE() AND blocked.OBJECT_NAME = ?
              AND held.OBJECT_SCHEMA = DATABASE() AND held.OBJECT_NAME = blocked.OBJECT_NAME
              AND held.INDEX_NAME = 'PRIMARY' AND held.LOCK_DATA = ?"""
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, table)
            statement.setString(2, id.toString())
            while (System.nanoTime() < deadline) {
                statement.executeQuery().use { rows -> if (rows.next() && rows.getInt(1) > 0) return@connectionUse true }
                Thread.sleep(POLL_MILLIS)
            }
            false
        }
    }

    init {
        extensions(SpringExtension())
        beforeEach {
            jdbc.update("DELETE FROM notification_outbox")
            jdbc.update("DELETE FROM user_terms")
            jdbc.update("DELETE FROM notice")
            jdbc.update("DELETE FROM terms")
            jdbc.update("UPDATE user SET notified = false")
            `when`(content.verifyContent(anyString(), anyString())).thenReturn(true)
        }
        given("AT-005-3 simultaneous resend requests") {
            `when`("the source is a notice") { then("one complete set is committed") { race(false, false) } }
            `when`("the source is terms") { then("one complete set is committed") { race(true, false) } }
        }
        given("AT-005-4 original AFTER_COMMIT and resend requests") {
            `when`("the source is a notice") { then("the original commits one complete set") { race(false, true) } }
            `when`("the source is terms") { then("the original commits one complete set") { race(true, true) } }
        }
    }

    companion object {
        private val mysql = MySQLContainer(DockerImageName.parse("mysql:8.0.41")).apply { start() }
        private const val TIMEOUT = 30L
        private const val WAIT_SECONDS = 10L
        private const val POLL_MILLIS = 50L
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.datasource.hikari.maximum-pool-size") { 6 }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.MySQLDialect" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector") { SqlPausePoint::class.java.name }
        }
    }
}
