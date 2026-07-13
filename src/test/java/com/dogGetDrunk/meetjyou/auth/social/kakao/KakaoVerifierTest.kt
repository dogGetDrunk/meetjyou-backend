package com.dogGetDrunk.meetjyou.auth.social.kakao

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
import java.time.Instant

class KakaoVerifierTest : BehaviorSpec() {

    private val props = mockk<KakaoOidcProperties>(relaxed = true)
    private val kakaoJwtDecoder: JwtDecoder = mockk()
    private val meterRegistry = SimpleMeterRegistry()
    private val sut = KakaoVerifier(props, kakaoJwtDecoder, meterRegistry)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        given("verifyAndExtract 호출 시 (IdToken)") {
            `when`("유효한 IdToken과 nonce가 일치하면") {
                then("SocialPrincipal을 반환한다") {
                    val jwt = buildJwt(subject = "kakao-sub-123", email = "user@example.com", nonce = "test-nonce")
                    every { kakaoJwtDecoder.decode(any()) } returns jwt

                    val result = sut.verifyAndExtract(IdToken("valid.id.token"), "test-nonce")

                    result.authProvider shouldBe AuthProvider.KAKAO
                    result.subject shouldBe "kakao-sub-123"
                    result.email shouldBe "user@example.com"
                }
            }

            `when`("nonce를 전달하지 않으면") {
                then("InvalidJwtException을 던진다") {
                    val jwt = buildJwt(subject = "kakao-sub-123", email = "user@example.com", nonce = "some-nonce")
                    every { kakaoJwtDecoder.decode(any()) } returns jwt

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(IdToken("valid.id.token"), nonce = null)
                    }
                }
            }

            `when`("nonce가 일치하지 않으면") {
                then("InvalidJwtException을 던진다") {
                    val jwt = buildJwt(subject = "kakao-sub-123", email = "user@example.com", nonce = "correct-nonce")
                    every { kakaoJwtDecoder.decode(any()) } returns jwt

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(IdToken("valid.id.token"), nonce = "wrong-nonce")
                    }
                }
            }

            `when`("email claim이 없으면") {
                then("InvalidJwtException을 던진다") {
                    val jwt = buildJwt(subject = "kakao-sub-123", email = null, nonce = "test-nonce")
                    every { kakaoJwtDecoder.decode(any()) } returns jwt

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(IdToken("valid.id.token"), nonce = "test-nonce")
                    }
                }
            }
        }
    }

    private fun buildJwt(subject: String, email: String?, nonce: String?): Jwt {
        val claims = mutableMapOf<String, Any>(
            "iss" to "https://kauth.kakao.com",
            "aud" to listOf("test-client-id"),
            "sub" to subject,
        )
        if (email != null) claims["email"] = email
        if (nonce != null) claims["nonce"] = nonce

        return Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            mapOf("alg" to "RS256"),
            claims,
        )
    }
}
