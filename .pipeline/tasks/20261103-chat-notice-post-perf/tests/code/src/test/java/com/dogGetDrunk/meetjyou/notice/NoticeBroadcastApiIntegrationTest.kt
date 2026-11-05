package com.dogGetDrunk.meetjyou.notice

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreference
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

private const val H2_URL = "jdbc:h2:mem:notice_broadcast_api;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE"

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=$H2_URL"],
)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class NoticeBroadcastApiIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var rest: TestRestTemplate
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var tx: TransactionTemplate

    private data class Fixture(
        val notice: UUID,
        val admin: UUID,
        val user: UUID,
        val enabledLater: UUID,
        val disabledLater: UUID,
        val joinedLater: UUID,
        val otherDisabled: UUID,
        val noPreference: UUID,
        val globalOff: UUID,
        val withdrawn: UUID,
    )

    private data class Row(
        val recipient: UUID,
        val type: NotificationType,
        val title: String?,
        val body: String?,
        val data: String,
        val key: String?,
    )

    private fun newUser(role: Role = Role.USER, notified: Boolean = true): User {
        val id = UUID.randomUUID().toString()
        return User(null, id.take(10), AuthProvider.KAKAO, id, role).also {
            it.notified = notified
            entityManager.persist(it)
        }
    }

    private fun preference(user: User, type: NotificationType, enabled: Boolean): UserNotificationPreference =
        UserNotificationPreference(user, type, enabled).also { entityManager.persist(it) }

    private fun fixture(): Fixture {
        val initial = tx.execute {
            val notice = Notice("Original title", "Original body")
            entityManager.persist(notice)
            val admin = newUser(Role.ADMIN, false)
            val user = newUser(notified = false)
            val enabledLater = newUser()
            val disabledLater = newUser()
            val otherDisabled = newUser()
            val noPreference = newUser()
            val globalOff = newUser(notified = false)
            val withdrawn = newUser().also { it.withdraw(Instant.now()) }
            preference(enabledLater, NotificationType.NOTICE, false)
            preference(disabledLater, NotificationType.NOTICE, true)
            preference(otherDisabled, NotificationType.CHAT_MESSAGE, false)
            listOf(notice.uuid, admin.uuid, user.uuid, enabledLater.uuid, disabledLater.uuid, otherDisabled.uuid,
                noPreference.uuid, globalOff.uuid, withdrawn.uuid)
        } ?: error("Fixture transaction failed")
        val joined = tx.execute {
            noticePreference(initial[3]).enabled = true
            noticePreference(initial[4]).enabled = false
            newUser().uuid
        } ?: error("Update transaction failed")
        return Fixture(
            initial[0], initial[1], initial[2], initial[3], initial[4],
            joined, initial[5], initial[6], initial[7], initial[8],
        )
    }

    private fun noticePreference(user: UUID): UserNotificationPreference = entityManager.createQuery(
        "select p from UserNotificationPreference p where p.user.uuid = :uuid and p.notificationType = :type",
        UserNotificationPreference::class.java,
    ).setParameter("uuid", user).setParameter("type", NotificationType.NOTICE).singleResult

    private fun expectedRecipients(critical: Boolean): Set<UUID> = tx.execute {
        val users = entityManager.createQuery("select u from User u where u.status = :status", User::class.java)
            .setParameter("status", UserStatus.NORMAL).resultList
        val disabled = entityManager.createQuery(
            """
            select p.user.uuid from UserNotificationPreference p
            where p.notificationType = :type and p.enabled = false
            """.trimIndent(),
            UUID::class.java,
        ).setParameter("type", NotificationType.NOTICE).resultList.toSet()
        users.filter { it.notified && (critical || it.uuid !in disabled) }.map { it.uuid }.toSet()
    } ?: error("Recipient transaction failed")

    private fun rows(uuid: UUID): List<Row> = tx.execute {
        entityManager.createQuery(
            "select o from NotificationOutbox o join fetch o.user where o.dedupKey like :prefix order by o.id",
            NotificationOutbox::class.java,
        ).setParameter("prefix", "notice:$uuid:%").resultList.map {
            Row(it.user.uuid, it.type, it.title, it.body, it.dataJson, it.dedupKey)
        }
    } ?: error("Outbox transaction failed")

    private fun post(uuid: UUID, user: UUID, role: Role, body: String): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(jwt.generateAccessToken(user, role))
        return rest.postForEntity("/api/v1/notices/$uuid/broadcast", HttpEntity(body, headers), String::class.java)
    }

    private fun assertRows(uuid: UUID, expected: Set<UUID>) {
        val actual = rows(uuid)
        actual.size shouldBe expected.size
        actual.map { it.recipient }.toSet() shouldBe expected
        actual.forEach {
            it.type shouldBe NotificationType.NOTICE
            it.title shouldBe "[공지사항]"
            it.body shouldBe "새로운 공지사항이 있어요."
            mapper.readTree(it.data) shouldBe mapper.readTree("""{"type":"NOTICE","noticeUuid":"$uuid"}""")
            it.key shouldBe "notice:$uuid:${it.recipient}"
        }
    }

    private fun assertSuccess(body: String, critical: Boolean) {
        val f = fixture()
        rows(f.notice) shouldBe emptyList()
        val expected = expectedRecipients(critical)
        setOf(f.enabledLater, f.joinedLater, f.otherDisabled, f.noPreference).all { it in expected } shouldBe true
        (f.disabledLater in expected) shouldBe critical
        (f.globalOff in expected) shouldBe false
        (f.withdrawn in expected) shouldBe false
        val response = post(f.notice, f.admin, Role.ADMIN, body)
        response.statusCode shouldBe HttpStatus.OK
        mapper.readTree(response.body).get("recipientCount").asInt() shouldBe expected.size
        assertRows(f.notice, expected)
        val before = rows(f.notice)
        val duplicate = post(f.notice, f.admin, Role.ADMIN, body)
        duplicate.statusCode shouldBe HttpStatus.CONFLICT
        mapper.readTree(duplicate.body).get("errorCode").asText() shouldBe "BROADCAST_ALREADY_SENT"
        rows(f.notice) shouldBe before
    }

    init {
        extensions(SpringExtension())

        given("AT-005-1 notice broadcast API with committed changes after notice creation") {
            `when`("an administrator broadcasts with default, false, and true requests") {
                then("recipients and payload follow the current settings and duplicate calls are rejected") {
                    assertSuccess("{}", false)
                    assertSuccess("""{"critical":false}""", false)
                    assertSuccess("""{"critical":true}""", true)
                }
            }
            `when`("a notice was already broadcast or is missing or a non-admin requests a broadcast") {
                then("the API returns 409, 404, and 403 without changing the outbox") {
                    val f = fixture()
                    val sent = tx.execute {
                        val notice = Notice("Sent", "Sent body")
                        entityManager.persist(notice)
                        val recipient = entityManager
                            .createQuery("select u from User u where u.uuid = :uuid", User::class.java)
                            .setParameter("uuid", f.enabledLater).singleResult
                        val key = "notice:${notice.uuid}:${recipient.uuid}"
                        entityManager.persist(
                            NotificationOutbox(recipient, NotificationType.NOTICE, "old", "old", "{}", key),
                        )
                        notice.uuid
                    } ?: error("Sent fixture transaction failed")
                    val beforeSent = rows(sent)
                    val already = post(sent, f.admin, Role.ADMIN, "{}")
                    already.statusCode shouldBe HttpStatus.CONFLICT
                    mapper.readTree(already.body).get("errorCode").asText() shouldBe "BROADCAST_ALREADY_SENT"
                    rows(sent) shouldBe beforeSent
                    val missing = UUID.randomUUID()
                    val beforeMissing = rows(missing)
                    val notFound = post(missing, f.admin, Role.ADMIN, "{}")
                    notFound.statusCode shouldBe HttpStatus.NOT_FOUND
                    mapper.readTree(notFound.body).get("errorCode").asText() shouldBe "NOTICE_NOT_FOUND"
                    rows(missing) shouldBe beforeMissing
                    val beforeForbidden = rows(f.notice)
                    post(f.notice, f.user, Role.USER, "{}").statusCode shouldBe HttpStatus.FORBIDDEN
                    rows(f.notice) shouldBe beforeForbidden
                }
            }
        }
    }
}
