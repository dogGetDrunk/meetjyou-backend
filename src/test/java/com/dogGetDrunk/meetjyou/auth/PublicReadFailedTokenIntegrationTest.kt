package com.dogGetDrunk.meetjyou.auth

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.notice.Notice
import com.dogGetDrunk.meetjyou.notice.NoticeRepository
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.version.AppVersionDto
import com.dogGetDrunk.meetjyou.version.AppVersionService
import com.dogGetDrunk.meetjyou.version.Platform
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * AT-001-2: the five public GET groups answer a failed Bearer token exactly like a token-less
 * request, while private endpoints and non-GET methods keep rejecting it. Runs the real filter chain.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class PublicReadFailedTokenIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var noticeRepository: NoticeRepository

    @Autowired
    private lateinit var appVersionService: AppVersionService

    @Value("\${local.server.port}")
    private var port: Int = 0

    private lateinit var normalUser: User
    private lateinit var withdrawnUser: User
    private lateinit var noticeUuid: UUID
    private lateinit var failedTokens: Map<String, String>

    private fun url(path: String) = "http://localhost:$port/api/v1$path"

    private fun get(path: String, bearer: String?) = send(HttpMethod.GET, path, bearer)

    private fun send(method: HttpMethod, path: String, bearer: String?, body: Any? = null) =
        restTemplate.exchange(url(path), method, HttpEntity(body, headers(bearer)), String::class.java)

    private fun headers(bearer: String?) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        bearer?.let { setBearerAuth(it) }
    }

    private fun publicGetPaths() = listOf(
        "/notices",
        "/notices/$noticeUuid",
        "/terms/active",
        "/ios/versions/check?clientVersion=$CLIENT_VERSION",
        "/android/versions/check?clientVersion=$CLIENT_VERSION",
        "/ios/versions/latest",
        "/android/versions/latest",
        "/users/is-duplicate-nickname?nickname=$NICKNAME_PROBE",
    )

    private fun tokenSignedWithOtherKey(): String =
        Jwts.builder()
            .subject(normalUser.uuid.toString())
            .claim("userUuid", normalUser.uuid.toString())
            .claim("role", Role.USER.name)
            .claim("token_type", "access")
            .expiration(Date(System.currentTimeMillis() + ONE_HOUR_MILLIS))
            .signWith(Keys.hmacShaKeyFor(OTHER_KEY_BYTES), Jwts.SIG.HS256)
            .compact()

    private fun seed() {
        normalUser = userRepository.save(UserFixtures.user(nickname = "pubnormal", externalId = "pub-normal"))
        val withdrawn = UserFixtures.user(nickname = "pubwithdr", externalId = "pub-withdrawn")
        withdrawn.withdraw(Instant.now())
        withdrawnUser = userRepository.save(withdrawn)
        noticeUuid = noticeRepository.save(Notice("title", "body")).uuid
        Platform.entries.forEach { seedVersion(it) }
        failedTokens = mapOf(
            "expired access" to jwtProvider.generateAccessToken(normalUser.uuid, Role.USER, EXPIRED_TTL_MILLIS),
            "other-key access" to tokenSignedWithOtherKey(),
            "malformed" to MALFORMED_TOKEN,
            "withdrawn user access" to jwtProvider.generateAccessToken(withdrawnUser.uuid, Role.USER),
            "refresh token" to jwtProvider.generateRefreshToken(normalUser.uuid).token,
        )
    }

    private fun seedVersion(platform: Platform) {
        val dto = AppVersionDto(platform, CLIENT_VERSION, forceUpdate = false, releasedAt = Instant.now())
        appVersionService.addVersion(platform, dto)
        appVersionService.setStoreReleased(CLIENT_VERSION, platform, true)
    }

    private fun cleanup() {
        Platform.entries.forEach { appVersionService.deleteVersion(CLIENT_VERSION, it) }
        noticeRepository.deleteAll()
        userRepository.deleteAll()
    }

    init {
        extensions(SpringExtension())

        beforeEach { seed() }
        afterEach { cleanup() }

        given("AT-001-2 공개 GET 경로에 토큰이 없을 때") {
            `when`("시드가 있는 공개 GET을 호출하면") {
                then("모두 200이다") {
                    publicGetPaths().forEach { get(it, null).statusCode.value() shouldBe HTTP_OK }
                }
            }
        }

        given("AT-001-2 공개 GET 경로에 실패한 토큰 5종이 붙었을 때") {
            `when`("각 경로를 토큰별로 호출하면") {
                then("토큰 없는 요청과 같은 상태 코드이고 시드가 있는 경로는 200이다") {
                    publicGetPaths().forEach { path ->
                        val anonymous = get(path, null).statusCode.value()
                        anonymous shouldBe HTTP_OK
                        failedTokens.forEach { (name, token) ->
                            withClue("$path with $name") { get(path, token).statusCode.value() shouldBe anonymous }
                        }
                    }
                }
            }
        }

        given("AT-001-2 대조: 비공개 GET에 실패한 토큰이 붙었을 때") {
            `when`("만료 / 형식 오류 access로 내 프로필을 조회하면") {
                then("401 EXPIRED_TOKEN / 401 INVALID_JWT 동작이 유지된다") {
                    val expired = get(PRIVATE_PATH, failedTokens.getValue("expired access"))
                    expired.statusCode.value() shouldBe HTTP_UNAUTHORIZED
                    expired.body.orEmpty() shouldContain "EXPIRED_TOKEN"
                    val malformed = get(PRIVATE_PATH, failedTokens.getValue("malformed"))
                    malformed.statusCode.value() shouldBe HTTP_UNAUTHORIZED
                    malformed.body.orEmpty() shouldContain "INVALID_JWT"
                }
            }
        }

        given("AT-001-2 대조: 공개 경로의 GET 외 메서드에 만료 토큰이 붙었을 때") {
            `when`("POST /notices를 호출하면") {
                then("401 EXPIRED_TOKEN 동작이 유지된다") {
                    val body = mapOf("title" to "t", "body" to "b")
                    val response = send(HttpMethod.POST, "/notices", failedTokens.getValue("expired access"), body)

                    response.statusCode.value() shouldBe HTTP_UNAUTHORIZED
                    response.body.orEmpty() shouldContain "EXPIRED_TOKEN"
                }
            }
        }

        given("AT-001-2 대조: 공개 GET에 NORMAL 유저의 유효 access가 붙었을 때") {
            `when`("공개 GET을 호출하면") {
                then("모두 200이다") {
                    val valid = jwtProvider.generateAccessToken(normalUser.uuid, Role.USER)

                    publicGetPaths().forEach { get(it, valid).statusCode.value() shouldBe HTTP_OK }
                }
            }
        }
    }

    private companion object {
        const val CLIENT_VERSION = "1.0.0"
        const val NICKNAME_PROBE = "probe"
        const val PRIVATE_PATH = "/users/me/profile"
        const val MALFORMED_TOKEN = "not.a.jwt"
        const val EXPIRED_TTL_MILLIS = -60_000L
        const val ONE_HOUR_MILLIS = 3_600_000L
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        val OTHER_KEY_BYTES = ByteArray(32) { (it + 1).toByte() }
    }
}
