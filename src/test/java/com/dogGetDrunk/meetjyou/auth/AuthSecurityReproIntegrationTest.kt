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
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.dto.NonceResponse
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
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
import org.springframework.test.context.ActiveProfiles

/**
 * Reproduces auth-protocol flaws through real HTTP requests against the full filter chain.
 * Only the Google signature check is mocked; nonce issuance, session handling, registration and
 * JWT authentication run as in production.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
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

    @MockBean
    private lateinit var firebaseApp: FirebaseApp

    @MockBean
    private lateinit var ociAuthProvider: AuthenticationDetailsProvider

    @MockBean
    private lateinit var objectStorageClient: ObjectStorageClient

    @MockBean
    private lateinit var workRequestClient: WorkRequestClient

    private fun url(path: String) = "http://localhost:$port/api/v1$path"

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
        val response = restTemplate.postForEntity(url("/auth/nonce"), null, NonceResponse::class.java)
        val sessionCookie = response.headers[HttpHeaders.SET_COOKIE]
            ?.first { it.startsWith("JSESSIONID") }
            ?.substringBefore(";")
            ?: throw IllegalStateException("No session cookie issued with nonce")
        val nonce = response.body?.nonce?.toString() ?: throw IllegalStateException("No nonce in body")
        return nonce to sessionCookie
    }

    /** Makes the Google verifier accept any token as a valid id_token carrying the given claims. */
    private fun stubGoogleIdToken(subject: String, verifiedEmail: String, nonce: String) {
        val payload = GoogleIdToken.Payload().apply {
            this.subject = subject
            this.email = verifiedEmail
            this.nonce = nonce
        }
        val idToken = GoogleIdToken(JsonWebSignature.Header(), payload, ByteArray(0), ByteArray(0))
        `when`(googleIdTokenVerifier.verify(anyString())).thenReturn(idToken)
    }

    private fun registerWithGoogle(claimedEmail: String, sessionCookie: String): TokenResponse {
        val request = RegistrationRequest(
            email = claimedEmail,
            nickname = "repro${System.nanoTime() % 1000}",
            bio = null,
            gender = Gender.M,
            age = Age.TWENTY,
            personalities = listOf(Personality.INTROVERTED),
            travelStyles = emptyList(),
            diet = emptyList(),
            etc = emptyList(),
            authProvider = AuthProvider.GOOGLE,
            idToken = "stubbed-google-id-token",
            agreedTermsUuids = emptyList(),
        )
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            add(HttpHeaders.COOKIE, sessionCookie)
        }
        val response = restTemplate.postForEntity(url("/auth/registration"), HttpEntity(request, headers), String::class.java)
        response.statusCode shouldBe HttpStatus.CREATED
        return objectMapper.readValue(response.body, TokenResponse::class.java)
    }

    private fun getMyProfileStatus(bearerToken: String): HttpStatus {
        val headers = HttpHeaders().apply { setBearerAuth(bearerToken) }
        val response = restTemplate.exchange(url("/users/me/profile"), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        return HttpStatus.valueOf(response.statusCode.value())
    }

    init {
        extensions(SpringExtension())

        beforeEach { seedPreferences() }
        afterEach { cleanup() }

        given("H1: 가입으로 발급받은 refresh token을") {
            `when`("access token 자리(Authorization 헤더)에 넣어 일반 API를 호출하면") {
                then("401로 거부되어야 한다") {
                    val (nonce, cookie) = issueNonce()
                    stubGoogleIdToken(subject = "google-sub-h1", verifiedEmail = "h1@gmail.com", nonce = nonce)
                    val tokens = registerWithGoogle(claimedEmail = "h1@gmail.com", sessionCookie = cookie)

                    getMyProfileStatus(tokens.accessToken) shouldBe HttpStatus.OK
                    getMyProfileStatus(tokens.refreshToken) shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        given("H2: 소셜 id_token이 검증한 이메일과 다른 이메일을 요청 body에 넣어 가입하면") {
            `when`("가입이 처리된 뒤 저장된 이메일을 보면") {
                then("body 값이 아니라 검증된 id_token의 이메일이어야 한다") {
                    val (nonce, cookie) = issueNonce()
                    stubGoogleIdToken(subject = "google-sub-attacker", verifiedEmail = "attacker@gmail.com", nonce = nonce)
                    val tokens = registerWithGoogle(claimedEmail = "victim@example.com", sessionCookie = cookie)

                    val saved = userRepository.findByUuid(tokens.uuid) ?: throw IllegalStateException("User not saved")
                    saved.email shouldBe "attacker@gmail.com"
                }
            }
        }
    }
}
