package com.dogGetDrunk.meetjyou.auth.social.apple

import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.AuthProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.security.MessageDigest
import java.time.Instant

class AppleVerifierTest : BehaviorSpec() {

    private val appleJwtDecoder: JwtDecoder = mockk()
    private val meterRegistry = SimpleMeterRegistry()
    private val sut = AppleVerifier(appleJwtDecoder, meterRegistry)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        given("verifyAndExtract 호출 시") {
            `when`("유효한 IdToken과 nonce가 일치하면") {
                then("SocialPrincipal을 반환한다") {
                    val rawNonce = "test-nonce"
                    // Apple은 SHA-256(rawNonce)를 ID Token에 담는다
                    val jwt = buildJwt(subject = "apple-sub-123", email = "user@example.com", nonceHash = sha256(rawNonce))
                    every { appleJwtDecoder.decode(any()) } returns jwt

                    val result = sut.verifyAndExtract(IdToken("valid.id.token"), rawNonce)

                    result.authProvider shouldBe AuthProvider.APPLE
                    result.subject shouldBe "apple-sub-123"
                    result.email shouldBe "user@example.com"
                }
            }

            `when`("nonce를 전달하지 않으면") {
                then("InvalidJwtException을 던진다") {
                    val jwt = buildJwt(subject = "apple-sub-123", email = "user@example.com", nonceHash = sha256("some-nonce"))
                    every { appleJwtDecoder.decode(any()) } returns jwt

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(IdToken("valid.id.token"), nonce = null)
                    }
                }
            }

            `when`("nonce가 일치하지 않으면") {
                then("InvalidJwtException을 던진다") {
                    val jwt = buildJwt(subject = "apple-sub-123", email = "user@example.com", nonceHash = sha256("correct-nonce"))
                    every { appleJwtDecoder.decode(any()) } returns jwt

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(IdToken("valid.id.token"), nonce = "wrong-nonce")
                    }
                }
            }

            `when`("email claim이 없으면") {
                then("InvalidJwtException을 던진다") {
                    val rawNonce = "test-nonce"
                    val jwt = buildJwt(subject = "apple-sub-123", email = null, nonceHash = sha256(rawNonce))
                    every { appleJwtDecoder.decode(any()) } returns jwt

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(IdToken("valid.id.token"), nonce = rawNonce)
                    }
                }
            }

            `when`("email_verified가 boolean true면") {
                then("SocialPrincipal을 반환한다") {
                    val rawNonce = "test-nonce"
                    val jwt = buildJwt("apple-sub-123", "user@example.com", sha256(rawNonce), emailVerified = true)
                    every { appleJwtDecoder.decode(any()) } returns jwt

                    sut.verifyAndExtract(IdToken("valid.id.token"), rawNonce).email shouldBe "user@example.com"
                }
            }

            `when`("email_verified가 false이거나 없으면") {
                then("InvalidJwtException을 던진다") {
                    val rawNonce = "test-nonce"
                    listOf("false", null).forEach { verified ->
                        val jwt = buildJwt("apple-sub-123", "user@example.com", sha256(rawNonce), verified)
                        every { appleJwtDecoder.decode(any()) } returns jwt

                        shouldThrow<InvalidJwtException> {
                            sut.verifyAndExtract(IdToken("valid.id.token"), nonce = rawNonce)
                        }
                    }
                }
            }

            `when`("AccessToken을 전달하면") {
                then("InvalidJwtException을 던진다") {
                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(AccessToken("some.access.token"), nonce = null)
                    }
                }
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun buildJwt(
        subject: String,
        email: String?,
        nonceHash: String? = null,
        emailVerified: Any? = "true",
    ): Jwt {
        val claims = mutableMapOf<String, Any>(
            "iss" to "https://appleid.apple.com",
            "aud" to listOf("com.example.app"),
            "sub" to subject,
        )
        if (email != null) claims["email"] = email
        if (nonceHash != null) claims["nonce"] = nonceHash
        if (emailVerified != null) claims["email_verified"] = emailVerified

        return Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            mapOf("alg" to "RS256"),
            claims,
        )
    }
}
