package com.dogGetDrunk.meetjyou.auth

import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.Preference
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.dto.NonceResponse
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.json.webtoken.JsonWebSignature
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.test.context.ActiveProfiles

/**
 * Drives the social sign-up/login flow and token usage through real HTTP requests against the
 * full filter chain. Only provider signature checks are mocked (Google verifier, Kakao decoder);
 * nonce issuance, session handling, registration and JWT authentication run as in production.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class AuthSecurityReproIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userPreferenceRepository: UserPreferenceRepository

    @Autowired
    private lateinit var preferenceRepository: PreferenceRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Value("\${local.server.port}")
    private var port: Int = 0

    @MockBean
    private lateinit var googleIdTokenVerifier: GoogleIdTokenVerifier

    @MockBean(name = "kakaoJwtDecoder")
    private lateinit var kakaoJwtDecoder: JwtDecoder

    // Auth endpoints are rate-limited per client IP; a distinct forwarded IP per test keeps the
    // tests from sharing buckets (requests arrive from 127.0.0.1, a trusted proxy for RemoteIpValve).
    private var clientIp = ""
    private var testCounter = 0

    private fun url(path: String) = "http://localhost:$port/api/v1$path"

    private fun post(path: String, entity: HttpEntity<*>): ResponseEntity<String> =
        restTemplate.postForEntity(url(path), entity, String::class.java)

    private fun ResponseEntity<*>.status(): HttpStatus = HttpStatus.valueOf(statusCode.value())

    private fun seedPreferences() {
        preferenceRepository.saveAll(
            listOf(
                Preference(PreferenceType.GENDER, Gender.M.name),
                Preference(PreferenceType.AGE, Age.TWENTY.name),
                Preference(PreferenceType.PERSONALITY, Personality.INTROVERTED.name),
            )
        )
    }

    private fun cleanup() {
        refreshTokenRepository.deleteAll()
        userPreferenceRepository.deleteAll()
        userRepository.deleteAll()
        preferenceRepository.deleteAll()
    }

    /** Issues a nonce and returns it with the session cookie that binds it. */
    private fun issueNonce(): Pair<String, String> {
        val response = restTemplate.postForEntity(
            url("/auth/nonce"), HttpEntity<Void>(jsonHeaders(null)), NonceResponse::class.java,
        )
        val sessionCookie = response.headers[HttpHeaders.SET_COOKIE]
            ?.first { it.startsWith("JSESSIONID") }
            ?.substringBefore(";")
            ?: error("No session cookie issued with nonce")
        val nonce = response.body?.nonce?.toString() ?: error("No nonce in body")
        return nonce to sessionCookie
    }

    /** Makes the Google verifier accept any token as a valid id_token carrying the given claims. */
    private fun stubGoogleIdToken(subject: String, email: String, nonce: String, emailVerified: Boolean = true) {
        val payload = GoogleIdToken.Payload().apply {
            this.subject = subject
            this.email = email
            this.emailVerified = emailVerified
            this.nonce = nonce
        }
        val idToken = GoogleIdToken(JsonWebSignature.Header(), payload, ByteArray(0), ByteArray(0))
        `when`(googleIdTokenVerifier.verify(anyString())).thenReturn(idToken)
    }

    private fun jsonHeaders(sessionCookie: String?, bearer: String? = null) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set(X_FORWARDED_FOR, clientIp)
        sessionCookie?.let { add(HttpHeaders.COOKIE, it) }
        bearer?.let { setBearerAuth(it) }
    }

    /**
     * Sent as a raw map so the body keeps an "email" field even if the DTO drops it — older app
     * builds still send one and must keep working.
     */
    private fun register(provider: AuthProvider, claimedEmail: String, sessionCookie: String): ResponseEntity<String> {
        val body = mapOf(
            "email" to claimedEmail,
            "nickname" to "repro${System.nanoTime() % NICKNAME_SUFFIX_RANGE}",
            "bio" to null,
            "gender" to Gender.M.name,
            "age" to Age.TWENTY.name,
            "personalities" to listOf(Personality.INTROVERTED.name),
            "travelStyles" to emptyList<String>(),
            "diet" to emptyList<String>(),
            "etc" to emptyList<String>(),
            "authProvider" to provider.name,
            "idToken" to STUB_ID_TOKEN,
            "agreedTermsUuids" to emptyList<String>(),
        )
        return post("/auth/registration", HttpEntity(body, jsonHeaders(sessionCookie)))
    }

    private fun registerOrFail(provider: AuthProvider, claimedEmail: String, sessionCookie: String): TokenResponse {
        val response = register(provider, claimedEmail, sessionCookie)
        response.statusCode shouldBe HttpStatus.CREATED
        return objectMapper.readValue(response.body, TokenResponse::class.java)
    }

    private fun login(provider: AuthProvider, sessionCookie: String): HttpStatus {
        val body = mapOf("authProvider" to provider.name, "idToken" to STUB_ID_TOKEN)
        return post("/auth/login", HttpEntity(body, jsonHeaders(sessionCookie))).status()
    }

    private fun getMyProfileStatus(bearerToken: String): HttpStatus {
        val entity = HttpEntity<Void>(jsonHeaders(null, bearerToken))
        return restTemplate.exchange(url("/users/me/profile"), HttpMethod.GET, entity, String::class.java).status()
    }

    private fun refreshStatus(bearerToken: String): HttpStatus {
        return post("/auth/refresh", HttpEntity<Void>(jsonHeaders(null, bearerToken))).status()
    }

    private fun logoutStatus(bearerToken: String): HttpStatus =
        post("/auth/logout", HttpEntity<Void>(jsonHeaders(null, bearerToken))).status()

    private fun signUpWithGoogle(subject: String, email: String): TokenResponse {
        val (nonce, cookie) = issueNonce()
        stubGoogleIdToken(subject = subject, email = email, nonce = nonce)
        return registerOrFail(AuthProvider.GOOGLE, email, cookie)
    }

    init {
        extensions(SpringExtension())

        beforeEach {
            testCounter++
            clientIp = "10.0.0.$testCounter"
            seedPreferences()
        }
        afterEach { cleanup() }

        given("H1: 가입으로 발급받은 refresh token을") {
            `when`("access token 자리(Authorization 헤더)에 넣어 일반 API를 호출하면") {
                then("401로 거부되어야 한다") {
                    val tokens = signUpWithGoogle(subject = "google-sub-h1", email = "h1@gmail.com")

                    getMyProfileStatus(tokens.accessToken) shouldBe HttpStatus.OK
                    getMyProfileStatus(tokens.refreshToken) shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        given("access token을") {
            `when`("refresh 엔드포인트에 넣으면") {
                then("401로 거부되고, 정상 refresh token은 회전된다") {
                    val tokens = signUpWithGoogle(subject = "google-sub-r1a", email = "r1a@gmail.com")

                    refreshStatus(tokens.accessToken) shouldBe HttpStatus.UNAUTHORIZED
                    refreshStatus(tokens.refreshToken) shouldBe HttpStatus.OK
                }
            }
        }

        given("관리자 승격 엔드포인트가 제거된 뒤") {
            `when`("로그인한 유저가 passphrase로 승격을 요청하면") {
                then("404를 받고 role은 USER로 남는다") {
                    val tokens = signUpWithGoogle(subject = "google-sub-admin", email = "admin-try@gmail.com")
                    val body = mapOf("passphrase" to "dev-admin-passphrase")
                    val request = HttpEntity(body, jsonHeaders(null, tokens.accessToken))

                    post("/auth/promote-admin", request).status() shouldBe HttpStatus.NOT_FOUND
                    userRepository.findByUuid(tokens.uuid)?.role shouldBe Role.USER
                }
            }
        }

        given("refresh token으로") {
            `when`("로그아웃하면") {
                then("204를 받고, 그 refresh token은 더 이상 회전되지 않는다") {
                    val tokens = signUpWithGoogle(subject = "google-sub-logout", email = "logout@gmail.com")

                    logoutStatus(tokens.refreshToken) shouldBe HttpStatus.NO_CONTENT
                    refreshStatus(tokens.refreshToken) shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        given("H2: 소셜 id_token이 검증한 이메일과 다른 이메일을 요청 body에 넣어 가입하면") {
            `when`("가입이 처리된 뒤 저장된 이메일을 보면") {
                then("body 값이 아니라 검증된 id_token의 이메일이어야 한다") {
                    val (nonce, cookie) = issueNonce()
                    stubGoogleIdToken(subject = "google-sub-attacker", email = "attacker@gmail.com", nonce = nonce)
                    val tokens = registerOrFail(AuthProvider.GOOGLE, "victim@example.com", cookie)

                    val saved = userRepository.findByUuid(tokens.uuid) ?: error("User not saved")
                    saved.email shouldBe "attacker@gmail.com"
                }
            }
        }

        given("Google id_token의 email_verified가 false이면") {
            `when`("가입을 요청할 때") {
                then("401로 거부되고 유저가 생성되지 않는다") {
                    val (nonce, cookie) = issueNonce()
                    stubGoogleIdToken("google-sub-unverified", "unverified@gmail.com", nonce, emailVerified = false)

                    val response = register(AuthProvider.GOOGLE, "unverified@gmail.com", cookie)

                    response.status() shouldBe HttpStatus.UNAUTHORIZED
                    userRepository.count() shouldBe 0L
                }
            }
        }

        given("미가입 유저가 nonce를 받아 로그인을 시도하고") {
            `when`("404를 받은 뒤 같은 세션·같은 id_token으로 가입하면") {
                then("가입이 성공한다") {
                    val (nonce, cookie) = issueNonce()
                    stubGoogleIdToken(subject = "google-sub-r3", email = "r3@gmail.com", nonce = nonce)

                    login(AuthProvider.GOOGLE, cookie) shouldBe HttpStatus.NOT_FOUND
                    register(AuthProvider.GOOGLE, "r3@gmail.com", cookie).status() shouldBe HttpStatus.CREATED
                }
            }
        }

        given("가입된 유저가 nonce를 받아 로그인에 성공한 뒤") {
            `when`("같은 세션·같은 id_token으로 다시 로그인하면") {
                then("nonce가 소비되어 401을 받는다") {
                    signUpWithGoogle(subject = "google-sub-replay", email = "replay@gmail.com")
                    val (nonce, cookie) = issueNonce()
                    stubGoogleIdToken(subject = "google-sub-replay", email = "replay@gmail.com", nonce = nonce)

                    login(AuthProvider.GOOGLE, cookie) shouldBe HttpStatus.OK
                    login(AuthProvider.GOOGLE, cookie) shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        given("nonce를 받아 가입에 성공한 뒤") {
            `when`("같은 세션·같은 id_token으로 로그인하면") {
                then("가입 때 nonce가 소비되어 401을 받는다") {
                    val (nonce, cookie) = issueNonce()
                    stubGoogleIdToken(subject = "google-sub-reg-replay", email = "reg-replay@gmail.com", nonce = nonce)

                    register(AuthProvider.GOOGLE, "reg-replay@gmail.com", cookie).status() shouldBe HttpStatus.CREATED
                    login(AuthProvider.GOOGLE, cookie) shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        given("Kakao JWK set을 가져오지 못하는 provider 장애 상황에서") {
            `when`("가입을 요청하면") {
                then("토큰 오류(401)로 위장되지 않고 서버 오류로 응답한다") {
                    val (_, cookie) = issueNonce()
                    val outage = JwtException("Couldn't retrieve remote JWK set")
                    `when`(kakaoJwtDecoder.decode(anyString())).thenThrow(outage)

                    val response = register(AuthProvider.KAKAO, "kakao@example.com", cookie)

                    response.status() shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                }
            }
        }

        given("만료된 Kakao id_token으로") {
            `when`("가입을 요청하면") {
                then("500이 아니라 401을 받는다") {
                    val (_, cookie) = issueNonce()
                    val expired = JwtValidationException(
                        "An error occurred while attempting to decode the Jwt: Jwt expired",
                        listOf(OAuth2Error("invalid_token", "Jwt expired", null)),
                    )
                    `when`(kakaoJwtDecoder.decode(anyString())).thenThrow(expired)

                    register(AuthProvider.KAKAO, "kakao@example.com", cookie).status() shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }

    private companion object {
        const val STUB_ID_TOKEN = "stubbed-id-token"
        const val NICKNAME_SUFFIX_RANGE = 1000
        const val X_FORWARDED_FOR = "X-Forwarded-For"
    }
}
