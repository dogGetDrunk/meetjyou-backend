package com.dogGetDrunk.meetjyou.auth.social.apple

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.ExternalServiceException
import com.dogGetDrunk.meetjyou.user.dto.NonceResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.security.MessageDigest
import java.time.Instant
import java.util.Date

/**
 * AT-004-2: POST /api/v1/auth/login with Apple ID tokens that carry missing or invalid claims.
 * The real AppleJwtDecoder, AppleVerifier and GlobalExceptionHandler run; only AppleJwksCache is
 * mocked (returns a test RSA public key or throws ExternalServiceException), so no network is used.
 * A 401 alone cannot prove the signature-first order; AT-004-1 asserts the cause at unit level.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class AppleInvalidClaimsLoginIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Value("\${local.server.port}")
    private var port: Int = 0

    @MockBean
    private lateinit var appleJwksCache: AppleJwksCache

    // Auth endpoints are rate-limited per client IP; a distinct forwarded IP per test avoids shared buckets.
    private var clientIp = ""
    private var testCounter = 0

    private fun url(path: String) = "http://localhost:$port/api/v1$path"

    private fun headers(sessionCookie: String?) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set(X_FORWARDED_FOR, clientIp)
        sessionCookie?.let { add(HttpHeaders.COOKIE, it) }
    }

    private data class NonceSession(val cookie: String, val nonce: String)

    /** Issues a nonce and returns it with the session cookie that binds it. */
    private fun issueNonce(): NonceSession {
        val response = restTemplate.postForEntity(
            url("/auth/nonce"), HttpEntity<Void>(headers(null)), NonceResponse::class.java,
        )
        val cookie = response.headers[HttpHeaders.SET_COOKIE]
            ?.first { it.startsWith("JSESSIONID") }
            ?.substringBefore(";")
            ?: error("No session cookie issued with nonce")
        val nonce = response.body?.nonce ?: error("No nonce in response")
        return NonceSession(cookie, nonce.toString())
    }

    // The token carries SHA-256 of the issued nonce, so AppleVerifier's nonce check passes and
    // only the claim under test can make the login fail.
    private fun loginWith(buildToken: (nonceHash: String) -> String): ResponseEntity<String> {
        val session = issueNonce()
        val body = mapOf("authProvider" to "APPLE", "idToken" to buildToken(sha256(session.nonce)))
        return restTemplate.postForEntity(
            url("/auth/login"), HttpEntity(body, headers(session.cookie)), String::class.java,
        )
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun errorCodeOf(response: ResponseEntity<String>): String? =
        objectMapper.readTree(response.body).get("errorCode")?.asText()

    private fun serveKey(key: RSAKey) {
        Mockito.`when`(appleJwksCache.getRsaKeyByKid(KID)).thenReturn(key.toPublicJWK())
    }

    /** Signs with [signingKey]; a claim passed as null is left out of the payload entirely. */
    private fun token(
        signingKey: RSAKey,
        nonceHash: String,
        expiresAt: Instant? = Instant.now().plusSeconds(TOKEN_LIFETIME_SECONDS),
        issuedAt: Instant? = Instant.now(),
        subject: String? = SUBJECT,
    ): String {
        val claims = JWTClaimsSet.Builder().issuer(ISSUER).audience(CLIENT_ID).claim(NONCE_CLAIM, nonceHash)
        subject?.let { claims.subject(it) }
        issuedAt?.let { claims.issueTime(Date.from(it)) }
        expiresAt?.let { claims.expirationTime(Date.from(it)) }
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), claims.build())
        jwt.sign(RSASSASigner(signingKey))
        return jwt.serialize()
    }

    private fun invalidTokens(): Map<String, (String) -> String> = mapOf(
        "valid signature, exp missing" to { hash -> token(SIGNING_KEY, hash, expiresAt = null) },
        "valid signature, iat missing" to { hash -> token(SIGNING_KEY, hash, issuedAt = null) },
        "valid signature, sub missing" to { hash -> token(SIGNING_KEY, hash, subject = null) },
        "valid signature, sub blank" to { hash -> token(SIGNING_KEY, hash, subject = BLANK_SUBJECT) },
        "wrong signature, expired exp" to { hash ->
            token(OTHER_KEY, hash, expiresAt = Instant.now().minusSeconds(EXPIRED_AGO_SECONDS))
        },
    )

    init {
        extensions(SpringExtension())

        beforeEach {
            testCounter++
            clientIp = "10.4.0.$testCounter"
        }

        invalidTokens().forEach { (case, buildToken) ->
            given("AT-004-2: Apple ID token ($case)") {
                `when`("POST /auth/login 으로 로그인하면") {
                    then("500이 아니라 401을 받는다") {
                        serveKey(SIGNING_KEY)

                        loginWith(buildToken).statusCode.value() shouldBe UNAUTHORIZED
                    }
                }
            }
        }

        given("AT-004-2: Apple JWKS 키 공급이 실패하는 상황에서") {
            `when`("유효한 서명의 ID token으로 로그인하면") {
                then("401로 위장되지 않고 503 SOCIAL_PROVIDER_UNAVAILABLE을 받는다") {
                    Mockito.`when`(appleJwksCache.getRsaKeyByKid(KID)).thenThrow(
                        ExternalServiceException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE, "apple-jwks"),
                    )

                    val response = loginWith { hash -> token(SIGNING_KEY, hash) }

                    response.statusCode.value() shouldBe SERVICE_UNAVAILABLE
                    errorCodeOf(response) shouldBe ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE.name
                }
            }
        }
    }

    private companion object {
        const val X_FORWARDED_FOR = "X-Forwarded-For"
        const val KID = "apple-it-key"
        const val ISSUER = "https://appleid.apple.com"
        const val CLIENT_ID = "test-apple-client"
        const val SUBJECT = "apple-sub-it"
        const val NONCE_CLAIM = "nonce"
        const val BLANK_SUBJECT = "   "
        const val RSA_KEY_SIZE = 2048
        const val TOKEN_LIFETIME_SECONDS = 600L
        const val EXPIRED_AGO_SECONDS = 3_600L
        const val UNAUTHORIZED = 401
        const val SERVICE_UNAVAILABLE = 503

        val SIGNING_KEY: RSAKey = RSAKeyGenerator(RSA_KEY_SIZE).keyID(KID).generate()
        val OTHER_KEY: RSAKey = RSAKeyGenerator(RSA_KEY_SIZE).keyID(KID).generate()
    }
}
