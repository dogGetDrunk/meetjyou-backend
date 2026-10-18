package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKey
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyRepository
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyScope
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreference
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.Preference
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Withdrawn users through the real HTTP chain and H2: a DELETED (in grace) or PURGED user has no
 * public profile, the admin list skips PURGED rows, and the purge job removes per-user settings.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class WithdrawnUserVisibilityIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var preferenceRepository: PreferenceRepository

    @Autowired
    private lateinit var userPreferenceRepository: UserPreferenceRepository

    @Autowired
    private lateinit var notificationPreferenceRepository: UserNotificationPreferenceRepository

    @Autowired
    private lateinit var idempotencyKeyRepository: IdempotencyKeyRepository

    @Autowired
    private lateinit var purgeService: WithdrawnUserPurgeService

    @Value("\${local.server.port}")
    private var port: Int = 0

    private var nicknameSeq = 0

    private fun url(path: String) = "http://localhost:$port/api/v1$path"

    private fun get(path: String, token: String): ResponseEntity<String> {
        val headers = HttpHeaders().apply { setBearerAuth(token) }
        return restTemplate.exchange(url(path), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
    }

    private fun ResponseEntity<String>.errorCode(): String =
        objectMapper.readValue(body, ErrorResponse::class.java).errorCode

    /** A user with every required preference, like one created through sign-up. */
    private fun saveUserWithPreferences(role: Role = Role.USER): User {
        nicknameSeq++
        val user = userRepository.save(
            User(
                email = null,
                nickname = "wv$nicknameSeq",
                authProvider = AuthProvider.KAKAO,
                externalId = "wv-ext-$nicknameSeq",
                role = role,
            )
        )
        val required = preferenceRepository.findAll()
        userPreferenceRepository.saveAll(required.map { UserPreference(user, it) })
        return user
    }

    private fun withdraw(user: User, at: Instant): User {
        user.withdraw(at)
        return userRepository.save(user)
    }

    private fun purge(user: User): User {
        val expired = withdraw(user, Instant.now().minus(WITHDRAWN_LONG_AGO))
        purgeService.purge(listOf(expired.id), Instant.now())
        return userRepository.findById(expired.id).orElseThrow()
    }

    private fun adminListUuids(token: String): List<UUID> {
        val response = get("/users?size=$ADMIN_PAGE_SIZE", token)
        response.statusCode.value() shouldBe HttpStatus.OK.value()
        val content = objectMapper.readTree(response.body).get("content") as ArrayNode
        return content.map { UUID.fromString(it.get("uuid").asText()) }
    }

    init {
        extensions(SpringExtension())

        beforeEach {
            preferenceRepository.saveAll(
                listOf(
                    Preference(PreferenceType.GENDER, Gender.M.name),
                    Preference(PreferenceType.AGE, Age.TWENTY.name),
                    Preference(PreferenceType.PERSONALITY, Personality.INTROVERTED.name),
                )
            )
        }
        afterEach {
            idempotencyKeyRepository.deleteAll()
            notificationPreferenceRepository.deleteAll()
            userPreferenceRepository.deleteAll()
            userRepository.deleteAll()
            preferenceRepository.deleteAll()
        }

        given("다른 유저의 공개 프로필을 조회할 때") {
            `when`("대상이 탈퇴 요청 후 유예 기간 중(DELETED)이면") {
                then("basic-info와 advanced-info 모두 USER_NOT_FOUND 404로 숨긴다") {
                    val viewer = saveUserWithPreferences()
                    val target = withdraw(saveUserWithPreferences(), Instant.now())
                    val token = jwtProvider.generateAccessToken(viewer.uuid, Role.USER)

                    for (path in listOf("basic-info", "advanced-info")) {
                        val response = get("/users/${target.uuid}/$path", token)
                        response.statusCode.value() shouldBe HttpStatus.NOT_FOUND.value()
                        response.errorCode() shouldBe USER_NOT_FOUND
                    }
                }
            }

            `when`("대상이 탈퇴 완료(PURGED)되어 선호 정보까지 삭제됐으면") {
                then("PREFERENCE_NOT_FOUND가 아니라 USER_NOT_FOUND 404를 준다") {
                    val viewer = saveUserWithPreferences()
                    val target = purge(saveUserWithPreferences())
                    val token = jwtProvider.generateAccessToken(viewer.uuid, Role.USER)

                    for (path in listOf("basic-info", "advanced-info")) {
                        val response = get("/users/${target.uuid}/$path", token)
                        response.statusCode.value() shouldBe HttpStatus.NOT_FOUND.value()
                        response.errorCode() shouldBe USER_NOT_FOUND
                    }
                }
            }

            `when`("대상이 정상(NORMAL) 유저면") {
                then("200으로 프로필을 준다") {
                    val viewer = saveUserWithPreferences()
                    val target = saveUserWithPreferences()
                    val token = jwtProvider.generateAccessToken(viewer.uuid, Role.USER)

                    get("/users/${target.uuid}/basic-info", token).statusCode.value() shouldBe HttpStatus.OK.value()
                }
            }
        }

        given("관리자 유저 목록에") {
            `when`("탈퇴 완료(PURGED) 유저가 섞여 있으면") {
                then("목록 전체가 404로 깨지지 않고, PURGED만 빠지며 DELETED는 남는다") {
                    val admin = saveUserWithPreferences(Role.ADMIN)
                    val normal = saveUserWithPreferences()
                    val deleted = withdraw(saveUserWithPreferences(), Instant.now())
                    purge(saveUserWithPreferences())
                    val token = jwtProvider.generateAccessToken(admin.uuid, Role.ADMIN)

                    adminListUuids(token) shouldContainExactlyInAnyOrder
                        listOf(admin.uuid, normal.uuid, deleted.uuid)
                }
            }
        }

        given("유예 기간이 끝난 유저를 purge하면") {
            `when`("알림 설정과 멱등성 키가 남아 있어도") {
                then("둘 다 삭제하고, 다른 유저의 행은 건드리지 않는다") {
                    val other = saveUserWithPreferences()
                    val target = saveUserWithPreferences()
                    for (user in listOf(target, other)) {
                        notificationPreferenceRepository.save(
                            UserNotificationPreference(user, NotificationType.CHAT_MESSAGE, enabled = false)
                        )
                        val key = IdempotencyKey(
                            scope = IdempotencyScope.CREATE_POST,
                            user = user,
                            idempotencyKey = "key-${user.id}",
                            resourceUuid = UUID.randomUUID(),
                            requestHash = "hash",
                        )
                        idempotencyKeyRepository.save(key)
                    }

                    purge(target)

                    notificationPreferenceRepository.findAll().map { it.user.id } shouldBe listOf(other.id)
                    idempotencyKeyRepository.findAll().map { it.user.id } shouldBe listOf(other.id)
                }
            }
        }
    }

    companion object {
        private const val USER_NOT_FOUND = "USER_NOT_FOUND"
        private const val ADMIN_PAGE_SIZE = 50
        private val WITHDRAWN_LONG_AGO: Duration = User.WITHDRAWAL_GRACE_PERIOD.plusDays(1)
    }
}
