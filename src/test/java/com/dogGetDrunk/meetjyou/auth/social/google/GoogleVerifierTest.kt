package com.dogGetDrunk.meetjyou.auth.social.google

import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.json.webtoken.JsonWebSignature
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll

class GoogleVerifierTest : BehaviorSpec() {

    private val googleIdTokenVerifier: GoogleIdTokenVerifier = mockk()
    private val meterRegistry = SimpleMeterRegistry()
    private val sut = GoogleVerifier(googleIdTokenVerifier, meterRegistry)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        given("verifyAndExtract 호출 시") {
            `when`("유효한 IdToken과 nonce가 일치하면") {
                then("SocialPrincipal을 반환한다") {
                    val token = buildGoogleIdToken(subject = "google-sub-123", email = "user@example.com", nonce = "test-nonce")
                    every { googleIdTokenVerifier.verify("valid.id.token") } returns token

                    val result = sut.verifyAndExtract(IdToken("valid.id.token"), "test-nonce")

                    result.authProvider shouldBe AuthProvider.GOOGLE
                    result.subject shouldBe "google-sub-123"
                    result.email shouldBe "user@example.com"
                }
            }

            `when`("nonce를 전달하지 않으면") {
                then("InvalidJwtException을 던진다") {
                    val token = buildGoogleIdToken(subject = "google-sub-123", email = "user@example.com", nonce = "some-nonce")
                    every { googleIdTokenVerifier.verify("valid.id.token") } returns token

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(IdToken("valid.id.token"), nonce = null)
                    }
                }
            }

            `when`("nonce가 일치하지 않으면") {
                then("InvalidJwtException을 던진다") {
                    val token = buildGoogleIdToken(subject = "google-sub-123", email = "user@example.com", nonce = "correct-nonce")
                    every { googleIdTokenVerifier.verify("valid.id.token") } returns token

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(IdToken("valid.id.token"), nonce = "wrong-nonce")
                    }
                }
            }

            `when`("토큰 검증에 실패하면") {
                then("InvalidJwtException을 던진다") {
                    every { googleIdTokenVerifier.verify("invalid.id.token") } returns null

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(IdToken("invalid.id.token"), nonce = "test-nonce")
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

    private fun buildGoogleIdToken(subject: String, email: String?, nonce: String?): GoogleIdToken {
        val payload = GoogleIdToken.Payload()
            .setSubject(subject)
            .also { p -> email?.let { p.email = it } }
            .also { p -> nonce?.let { p.setNonce(it) } }
        val header = JsonWebSignature.Header()
        return GoogleIdToken(header, payload, ByteArray(0), ByteArray(0))
    }
}
