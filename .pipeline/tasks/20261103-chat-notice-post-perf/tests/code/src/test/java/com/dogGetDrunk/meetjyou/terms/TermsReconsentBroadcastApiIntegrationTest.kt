package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.exception.business.terms.TermsReconsentNotApplicableException
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.broadcast.TermsReconsentBroadcaster
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreference
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreferenceRepository
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplateFactory
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

private const val H2_URL = "jdbc:h2:mem:terms_reconsent_api;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE"

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = ["spring.datasource.url=$H2_URL"],
)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class TermsReconsentBroadcastApiIntegrationTest : FunSpec() {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var terms: TermsRepository
    @Autowired private lateinit var consents: UserTermsRepository
    @Autowired private lateinit var preferences: UserNotificationPreferenceRepository
    @Autowired private lateinit var outbox: NotificationOutboxRepository
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var tx: TransactionTemplate
    @Value("\${local.server.port}") private var port: Int = 0
    @MockBean private lateinit var contentGenerator: TermsContentUrlGenerator

    private fun user(role: Role = Role.USER): User {
        val key = UUID.randomUUID().toString()
        return users.saveAndFlush(User(null, key.take(10), AuthProvider.KAKAO, key, role))
    }

    private fun term(type: TermsType, status: TermsStatus, effectiveAt: Instant): Terms = terms.saveAndFlush(
        Terms(type, UUID.randomUUID().toString().take(20), "이용약관", true, "test", "hash", status, effectiveAt),
    )

    private fun request(path: String, actor: User, body: Any? = null) = http.postForEntity(
        "http://localhost:$port/api/v1/terms$path",
        HttpEntity(body, HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(jwt.generateAccessToken(actor.uuid, actor.role))
        }),
        String::class.java,
    )

    private fun rows(uuid: UUID) = tx.execute {
        outbox.findAll().filter { it.dedupKey?.startsWith("terms_reconsent:$uuid:") == true }
            .map { listOf(it.user.uuid.toString(), it.type.name, it.title, it.body, it.dataJson, it.dedupKey) }
    } ?: error("Transaction returned no rows")

    private fun error(response: org.springframework.http.ResponseEntity<String>, status: HttpStatus, code: String) {
        response.statusCode shouldBe status
        mapper.readTree(response.body).path("errorCode").asText() shouldBe code
    }

    init {
        extensions(SpringExtension())

        beforeTest {
            tx.executeWithoutResult {
                outbox.deleteAllInBatch()
                consents.deleteAllInBatch()
                preferences.deleteAllInBatch()
                terms.deleteAllInBatch()
            }
        }

        test("AT-005-2 resend uses current recipients and rejects duplicates") {
            val admin = user(Role.ADMIN)
            val previous = term(TermsType.TERMS_OF_SERVICE, TermsStatus.INACTIVE, Instant.now().minusSeconds(3600))
            val current = term(previous.type, TermsStatus.ACTIVE, Instant.now().minusSeconds(3600))
            val newlyJoined = user()
            val enabledLater = user()
            val disabledLater = user()
            val globallyDisabled = user()
            val withdrawn = user()
            listOf(enabledLater, disabledLater, globallyDisabled, withdrawn).forEach {
                consents.saveAndFlush(UserTerms(previous, it))
            }
            val enabledPreference = preferences.saveAndFlush(
                UserNotificationPreference(enabledLater, NotificationType.TERMS_RECONSENT, false),
            )
            val disabledPreference = preferences.saveAndFlush(
                UserNotificationPreference(disabledLater, NotificationType.TERMS_RECONSENT, true),
            )
            consents.saveAndFlush(UserTerms(previous, newlyJoined))
            enabledPreference.enabled = true
            disabledPreference.enabled = false
            preferences.saveAllAndFlush(listOf(enabledPreference, disabledPreference))
            globallyDisabled.notified = false
            withdrawn.withdraw(Instant.now())
            users.saveAllAndFlush(listOf(globallyDisabled, withdrawn))
            rows(current.uuid).size shouldBe 0
            val response = request("/${current.uuid}/reconsent-broadcast", admin)
            response.statusCode shouldBe HttpStatus.OK
            mapper.readTree(response.body).path("recipientCount").asInt() shouldBe 2
            val actual = rows(current.uuid)
            actual.map { it[0] } shouldContainExactlyInAnyOrder
                listOf(newlyJoined.uuid.toString(), enabledLater.uuid.toString())
            actual.forEach {
                it[1] shouldBe "TERMS_RECONSENT"
                it[2] shouldBe "[약관 변경 안내]"
                it[3] shouldBe "이용약관 이(가) 변경되었어요. 확인 후 다시 동의해주세요."
                mapper.readTree(it[4]).path("type").asText() shouldBe "TERMS_RECONSENT"
                mapper.readTree(it[4]).path("termsUuid").asText() shouldBe current.uuid.toString()
                it[5] shouldBe "terms_reconsent:${current.uuid}:${it[0]}"
            }
            error(request("/${current.uuid}/reconsent-broadcast", admin), HttpStatus.CONFLICT, "BROADCAST_ALREADY_SENT")
            rows(current.uuid) shouldBe actual
            request("/${current.uuid}/reconsent-broadcast", newlyJoined).statusCode shouldBe HttpStatus.FORBIDDEN
            rows(current.uuid) shouldBe actual
            val missing = UUID.randomUUID()
            error(request("/$missing/reconsent-broadcast", admin), HttpStatus.NOT_FOUND, "TERMS_NOT_FOUND")
            rows(missing).size shouldBe 0
            rows(current.uuid) shouldBe actual
            tx.execute { terms.findByUuid(current.uuid)?.status } shouldBe TermsStatus.ACTIVE
            tx.execute { terms.findByUuid(previous.uuid)?.status } shouldBe TermsStatus.INACTIVE
        }

        test("AT-005-2 each ineligible state is rejected") {
            val admin = user(Role.ADMIN)
            val old = term(TermsType.PRIVACY_COLLECTION_USE, TermsStatus.INACTIVE, Instant.now().minusSeconds(3600))
            val rejected = listOf(
                term(old.type, TermsStatus.DRAFT, Instant.now().minusSeconds(3600)),
                term(old.type, TermsStatus.INACTIVE, Instant.now().minusSeconds(3600)),
                term(old.type, TermsStatus.ACTIVE, Instant.now().plusSeconds(3600)),
                term(TermsType.AGE_OVER_14, TermsStatus.ACTIVE, Instant.now().minusSeconds(3600)),
                term(TermsType.MARKETING_SNS_EVENTS, TermsStatus.ACTIVE, Instant.now().minusSeconds(3600)),
            )
            rejected.forEach { target ->
                error(
                    request("/${target.uuid}/reconsent-broadcast", admin),
                    HttpStatus.CONFLICT,
                    "TERMS_RECONSENT_NOT_APPLICABLE",
                )
                rows(target.uuid).size shouldBe 0
                tx.execute { terms.findByUuid(target.uuid)?.status } shouldBe target.status
            }
        }

        test("AT-005-2 original publication sends even when effective in future") {
            val admin = user(Role.ADMIN)
            val previous = term(TermsType.MARKETING_EMAIL_EVENTS, TermsStatus.ACTIVE, Instant.now().minusSeconds(3600))
            val recipient = user()
            consents.saveAndFlush(UserTerms(previous, recipient))
            `when`(contentGenerator.verifyContent(anyString(), anyString())).thenReturn(true)
            val response = request("", admin, mapOf(
                "type" to previous.type.name, "version" to UUID.randomUUID().toString().take(20),
                "displayText" to "미래 약관", "required" to true, "contentHash" to "hash",
                "effectiveAt" to Instant.now().plusSeconds(3600).toString(),
            ))
            response.statusCode shouldBe HttpStatus.OK
            val uuid = UUID.fromString(mapper.readTree(response.body).path("termsUuid").asText())
            val actual = rows(uuid)
            actual.size shouldBe 1
            actual.single()[0] shouldBe recipient.uuid.toString()
            actual.single()[1] shouldBe "TERMS_RECONSENT"
            actual.single()[2] shouldBe "[약관 변경 안내]"
            actual.single()[3] shouldBe "미래 약관 이(가) 변경되었어요. 확인 후 다시 동의해주세요."
            mapper.readTree(actual.single()[4]).path("type").asText() shouldBe "TERMS_RECONSENT"
            mapper.readTree(actual.single()[4]).path("termsUuid").asText() shouldBe uuid.toString()
            actual.single()[5] shouldBe "terms_reconsent:$uuid:${recipient.uuid}"
            tx.execute { terms.findByUuid(previous.uuid)?.status } shouldBe TermsStatus.INACTIVE
            tx.execute { terms.findByUuid(uuid)?.status } shouldBe TermsStatus.ACTIVE
        }

        test("AT-005-2 fixed clock accepts equality and rejects one nanosecond later after lock") {
            TermsReconsentBroadcaster::class.java.getMethod("resend", UUID::class.java)
                .getAnnotation(Transactional::class.java).propagation shouldBe Propagation.REQUIRES_NEW
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val repository = mockk<TermsRepository>()
            val userTerms = mockk<UserTermsRepository>()
            val prefs = mockk<UserNotificationPreferenceRepository>()
            val box = mockk<NotificationOutboxRepository>()
            val factory = mockk<NotificationTemplateFactory>(relaxed = true)
            val broadcaster = TermsReconsentBroadcaster(
                repository,
                userTerms,
                prefs,
                box,
                factory,
                ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC),
            )
            val equal = Terms(TermsType.AGE_OVER_14, "1", "약관", true, "key", "hash", TermsStatus.ACTIVE, now)
            val future = Terms(
                TermsType.AGE_OVER_14, "2", "약관", true, "key", "hash", TermsStatus.ACTIVE, now.plusNanos(1),
            )
            every { repository.findByUuidForUpdate(equal.uuid) } returns equal
            every { repository.findByUuidForUpdate(future.uuid) } returns future
            every { repository.existsByTypeAndStatus(equal.type, TermsStatus.INACTIVE) } returns true
            every { box.existsByDedupKeyStartingWith(any()) } returns false
            every { userTerms.findLatestByTermsType(equal.type, TermsAgreementAction.AGREED) } returns emptyList()
            every { prefs.findDisabledUserIdsByType(NotificationType.TERMS_RECONSENT) } returns emptySet()
            every { box.saveAll(any<Iterable<NotificationOutbox>>()) } returns emptyList()
            broadcaster.resend(equal.uuid) shouldBe 0
            verifyOrder {
                repository.findByUuidForUpdate(equal.uuid)
                repository.existsByTypeAndStatus(equal.type, TermsStatus.INACTIVE)
                box.existsByDedupKeyStartingWith("terms_reconsent:${equal.uuid}:")
            }
            shouldThrow<TermsReconsentNotApplicableException> { broadcaster.resend(future.uuid) }
            verify(exactly = 1) { repository.findByUuidForUpdate(future.uuid) }
            verify(exactly = 0) { box.existsByDedupKeyStartingWith("terms_reconsent:${future.uuid}:") }
        }
    }
}
