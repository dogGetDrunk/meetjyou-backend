package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.Preference
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.dto.NonceResponse
import com.dogGetDrunk.meetjyou.user.dto.SocialAccountResponse
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import com.fasterxml.jackson.module.kotlin.readValue
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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger
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
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles

/**
 * Drives /users/me/social-accounts through real HTTP requests and the full filter chain, then
 * proves the effect through social login itself. Only provider signature checks are mocked.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class SocialAccountIntegrationTest : BehaviorSpec() {

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

    // Auth endpoints are rate-limited per client IP; a distinct forwarded IP per test avoids shared buckets.
    private var clientIp = ""
    private var testCounter = 0
    private val nicknameCounter = AtomicInteger()

    private fun url(path: String) = "http://localhost:$port/api/v1$path"

    private fun ResponseEntity<*>.status(): HttpStatus = HttpStatus.valueOf(statusCode.value())

    private fun headers(sessionCookie: String? = null, bearer: String? = null) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set(X_FORWARDED_FOR, clientIp)
        sessionCookie?.let { add(HttpHeaders.COOKIE, it) }
        bearer?.let { setBearerAuth(it) }
    }

    private fun exchange(path: String, method: HttpMethod, entity: HttpEntity<*>): ResponseEntity<String> =
        restTemplate.exchange(url(path), method, entity, String::class.java)

    /** Issues a nonce and returns it with the session cookie that binds it. */
    private fun issueNonce(): Pair<String, String> {
        val response = restTemplate.postForEntity(
            url("/auth/nonce"), HttpEntity<Void>(headers()), NonceResponse::class.java,
        )
        val cookie = response.headers[HttpHeaders.SET_COOKIE]
            ?.first { it.startsWith("JSESSIONID") }
            ?.substringBefore(";")
            ?: error("No session cookie issued with nonce")
        val nonce = response.body?.nonce?.toString() ?: error("No nonce in body")
        return nonce to cookie
    }

    private fun stubGoogle(subject: String, email: String, nonce: String) {
        val payload = GoogleIdToken.Payload().apply {
            this.subject = subject
            this.email = email
            this.emailVerified = true
            this.nonce = nonce
        }
        `when`(googleIdTokenVerifier.verify(anyString()))
            .thenReturn(GoogleIdToken(JsonWebSignature.Header(), payload, ByteArray(0), ByteArray(0)))
    }

    private fun stubKakao(subject: String, email: String, nonce: String) {
        val jwt = Jwt.withTokenValue(STUB_ID_TOKEN)
            .header("alg", "RS256")
            .subject(subject)
            .claim("nonce", nonce)
            .claim("email", email)
            .build()
        `when`(kakaoJwtDecoder.decode(anyString())).thenReturn(jwt)
    }

    private fun signUpWithGoogle(subject: String): TokenResponse {
        val (nonce, cookie) = issueNonce()
        stubGoogle(subject, "$subject@gmail.com", nonce)
        val body = mapOf(
            "nickname" to "link${nicknameCounter.incrementAndGet()}",
            "bio" to null,
            "gender" to Gender.M.name,
            "age" to Age.TWENTY.name,
            "personalities" to listOf(Personality.INTROVERTED.name),
            "travelStyles" to emptyList<String>(),
            "diet" to emptyList<String>(),
            "etc" to emptyList<String>(),
            "authProvider" to AuthProvider.GOOGLE.name,
            "idToken" to STUB_ID_TOKEN,
            "agreedTermsUuids" to emptyList<String>(),
        )
        val response = exchange("/auth/registration", HttpMethod.POST, HttpEntity(body, headers(cookie)))
        response.status() shouldBe HttpStatus.CREATED
        return objectMapper.readValue(response.body, TokenResponse::class.java)
    }

    private fun linkKakao(accessToken: String, kakaoSubject: String): ResponseEntity<String> {
        val (nonce, cookie) = issueNonce()
        stubKakao(kakaoSubject, "$kakaoSubject@kakao.com", nonce)
        val body = mapOf("authProvider" to AuthProvider.KAKAO.name, "idToken" to STUB_ID_TOKEN)
        return exchange(SOCIAL_ACCOUNTS, HttpMethod.POST, HttpEntity(body, headers(cookie, accessToken)))
    }

    private fun loginWithKakao(kakaoSubject: String): ResponseEntity<String> {
        val (nonce, cookie) = issueNonce()
        stubKakao(kakaoSubject, "$kakaoSubject@kakao.com", nonce)
        val body = mapOf("authProvider" to AuthProvider.KAKAO.name, "idToken" to STUB_ID_TOKEN)
        return exchange("/auth/login", HttpMethod.POST, HttpEntity(body, headers(cookie)))
    }

    private fun unlink(accessToken: String, provider: AuthProvider): HttpStatus {
        val entity = HttpEntity<Void>(headers(bearer = accessToken))
        return exchange("$SOCIAL_ACCOUNTS/${provider.name}", HttpMethod.DELETE, entity).status()
    }

    private fun myProviders(accessToken: String): List<AuthProvider> {
        val response = exchange(SOCIAL_ACCOUNTS, HttpMethod.GET, HttpEntity<Void>(headers(bearer = accessToken)))
        response.status() shouldBe HttpStatus.OK
        return objectMapper.readValue<List<SocialAccountResponse>>(response.body ?: "[]").map { it.provider }
    }

    init {
        extensions(SpringExtension())

        beforeEach {
            testCounter++
            clientIp = "10.1.0.$testCounter"
            preferenceRepository.saveAll(
                listOf(
                    Preference(PreferenceType.GENDER, Gender.M.name),
                    Preference(PreferenceType.AGE, Age.TWENTY.name),
                    Preference(PreferenceType.PERSONALITY, Personality.INTROVERTED.name),
                )
            )
        }
        afterEach {
            refreshTokenRepository.deleteAll()
            userPreferenceRepository.deleteAll()
            userRepository.deleteAll()
            preferenceRepository.deleteAll()
        }

        given("구글로 가입한 유저가") {
            `when`("카카오를 연결했다가 해제하면") {
                then("연결 중에는 카카오 로그인이 같은 계정으로 되고, 해제 후에는 404가 된다") {
                    val tokens = signUpWithGoogle("google-link-1")

                    linkKakao(tokens.accessToken, "kakao-link-1").status() shouldBe HttpStatus.CREATED
                    myProviders(tokens.accessToken) shouldContainExactly listOf(AuthProvider.GOOGLE, AuthProvider.KAKAO)

                    val login = loginWithKakao("kakao-link-1")
                    login.status() shouldBe HttpStatus.OK
                    objectMapper.readValue(login.body, TokenResponse::class.java).uuid shouldBe tokens.uuid

                    unlink(tokens.accessToken, AuthProvider.KAKAO) shouldBe HttpStatus.NO_CONTENT
                    loginWithKakao("kakao-link-1").status() shouldBe HttpStatus.NOT_FOUND
                    myProviders(tokens.accessToken) shouldContainExactly listOf(AuthProvider.GOOGLE)
                }
            }

            `when`("같은 카카오 계정 연결을 다시 요청하면") {
                then("두 번째는 200이고 계정은 하나만 연결된다") {
                    val tokens = signUpWithGoogle("google-link-2")

                    linkKakao(tokens.accessToken, "kakao-link-2").status() shouldBe HttpStatus.CREATED
                    linkKakao(tokens.accessToken, "kakao-link-2").status() shouldBe HttpStatus.OK
                    myProviders(tokens.accessToken) shouldContainExactly listOf(AuthProvider.GOOGLE, AuthProvider.KAKAO)
                }
            }

            `when`("다른 유저가 이미 연결한 카카오 계정을 연결하면") {
                then("409를 받고 두 유저의 연결은 그대로다") {
                    val owner = signUpWithGoogle("google-owner")
                    linkKakao(owner.accessToken, "kakao-shared").status() shouldBe HttpStatus.CREATED
                    val other = signUpWithGoogle("google-other")

                    linkKakao(other.accessToken, "kakao-shared").status() shouldBe HttpStatus.CONFLICT
                    myProviders(other.accessToken) shouldContainExactly listOf(AuthProvider.GOOGLE)
                    myProviders(owner.accessToken) shouldContainExactly listOf(AuthProvider.GOOGLE, AuthProvider.KAKAO)
                }
            }

            `when`("유일한 구글 계정을 해제하면") {
                then("409(상태 충돌)를 받고 구글 로그인 수단이 남는다") {
                    val tokens = signUpWithGoogle("google-last")

                    unlink(tokens.accessToken, AuthProvider.GOOGLE) shouldBe HttpStatus.CONFLICT
                    unlink(tokens.accessToken, AuthProvider.APPLE) shouldBe HttpStatus.NOT_FOUND
                    myProviders(tokens.accessToken) shouldContainExactly listOf(AuthProvider.GOOGLE)
                }
            }
        }

        given("access token 없이") {
            `when`("소셜 계정 API를 호출하면") {
                then("401을 받는다") {
                    exchange(SOCIAL_ACCOUNTS, HttpMethod.GET, HttpEntity<Void>(headers())).status() shouldBe
                        HttpStatus.UNAUTHORIZED
                }
            }
        }
    }

    private companion object {
        const val SOCIAL_ACCOUNTS = "/users/me/social-accounts"
        const val STUB_ID_TOKEN = "stubbed-id-token"
        const val X_FORWARDED_FOR = "X-Forwarded-For"
    }
}
