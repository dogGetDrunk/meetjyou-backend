package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.ExternalServiceException
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.io.IOException
import java.time.Instant

class KakaoVerifierTest : BehaviorSpec() {

    private val props = mockk<KakaoOidcProperties>(relaxed = true)
    private val kakaoJwtDecoder: JwtDecoder = mockk()
    private val meterRegistry = SimpleMeterRegistry()
    private val restClientBuilder = RestClient.builder()
    private val kakaoApi = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val sut = KakaoVerifier(props, kakaoJwtDecoder, meterRegistry, restClientBuilder)

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

            `when`("토큰 자체가 거절되면(BadJwtException)") {
                then("클라이언트 오류 그대로 전파한다") {
                    every { kakaoJwtDecoder.decode(any()) } throws BadJwtException("Jwt expired")

                    shouldThrow<BadJwtException> {
                        sut.verifyAndExtract(IdToken("expired.id.token"), nonce = "test-nonce")
                    }
                }
            }

            `when`("Kakao JWK set을 가져오지 못하면") {
                then("공급자 장애(ExternalServiceException, 503)로 던진다") {
                    every { kakaoJwtDecoder.decode(any()) } throws JwtException("Couldn't retrieve remote JWK set")

                    val e = shouldThrow<ExternalServiceException> {
                        sut.verifyAndExtract(IdToken("valid.id.token"), nonce = "test-nonce")
                    }
                    e.errorCode shouldBe ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE
                }
            }

            `when`("email claim이 없으면(이메일 제공 미동의)") {
                then("email 없이 SocialPrincipal을 반환한다") {
                    val jwt = buildJwt(subject = "kakao-sub-123", email = null, nonce = "test-nonce")
                    every { kakaoJwtDecoder.decode(any()) } returns jwt

                    val result = sut.verifyAndExtract(IdToken("valid.id.token"), nonce = "test-nonce")

                    result.subject shouldBe "kakao-sub-123"
                    result.email shouldBe null
                }
            }
        }

        given("verifyAndExtract 호출 시 (AccessToken)") {
            `when`("Kakao가 snake_case로 응답하고 app_id가 일치하면") {
                then("SocialPrincipal을 반환한다") {
                    stubKakaoUris()
                    expectTokenInfo(appId = APP_ID)
                    expectUserInfo()

                    val result = sut.verifyAndExtract(AccessToken(ACCESS_TOKEN), nonce = null)

                    result.authProvider shouldBe AuthProvider.KAKAO
                    result.subject shouldBe KAKAO_USER_ID.toString()
                    result.email shouldBe "user@example.com"
                    kakaoApi.verify()
                }
            }

            `when`("app_id가 우리 앱과 다르면") {
                then("InvalidJwtException을 던진다") {
                    stubKakaoUris()
                    expectTokenInfo(appId = OTHER_APP_ID)

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(AccessToken(ACCESS_TOKEN), nonce = null)
                    }
                }
            }

            `when`("Kakao가 401로 토큰을 거절하면(만료·잘못된 토큰)") {
                then("500이 아닌 InvalidJwtException(401)을 던진다") {
                    kakaoApi.reset()
                    stubKakaoUris()
                    kakaoApi.expect(requestTo(TOKEN_INFO_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

                    shouldThrow<InvalidJwtException> {
                        sut.verifyAndExtract(AccessToken(ACCESS_TOKEN), nonce = null)
                    }
                }
            }

            listOf(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.TOO_MANY_REQUESTS).forEach { status ->
                `when`("Kakao가 ${status.value()}로 응답하면") {
                    then("공급자 장애(ExternalServiceException, 503)로 던진다") {
                        kakaoApi.reset()
                        stubKakaoUris()
                        kakaoApi.expect(requestTo(TOKEN_INFO_URI)).andRespond(withStatus(status))

                        val e = shouldThrow<ExternalServiceException> {
                            sut.verifyAndExtract(AccessToken(ACCESS_TOKEN), nonce = null)
                        }
                        e.errorCode shouldBe ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE
                    }
                }
            }

            `when`("Kakao에 연결하지 못하면") {
                then("공급자 장애(ExternalServiceException, 503)로 던진다") {
                    kakaoApi.reset()
                    stubKakaoUris()
                    kakaoApi.expect(requestTo(TOKEN_INFO_URI))
                        .andRespond(withException(IOException("Connection refused")))

                    shouldThrow<ExternalServiceException> {
                        sut.verifyAndExtract(AccessToken(ACCESS_TOKEN), nonce = null)
                    }
                }
            }
        }
    }

    private fun stubKakaoUris() {
        every { props.accessTokenInfoUri } returns TOKEN_INFO_URI
        every { props.userInfoUri } returns USER_INFO_URI
        every { props.appId } returns APP_ID.toString()
    }

    // Response bodies mirror the real Kakao API shape (snake_case, extra fields included).
    private fun expectTokenInfo(appId: Long) {
        kakaoApi.expect(requestTo(TOKEN_INFO_URI))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer $ACCESS_TOKEN"))
            .andRespond(
                withSuccess(
                    """{"id":$KAKAO_USER_ID,"expires_in":7199,"app_id":$appId}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
    }

    private fun expectUserInfo() {
        kakaoApi.expect(requestTo(USER_INFO_URI))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                    {"id":$KAKAO_USER_ID,"connected_at":"2024-01-01T00:00:00Z",
                     "kakao_account":{"email":"user@example.com","is_email_valid":true,"is_email_verified":true}}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
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

    companion object {
        private const val TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info"
        private const val USER_INFO_URI = "https://kapi.kakao.com/v2/user/me"
        private const val ACCESS_TOKEN = "kakao-access-token"
        private const val KAKAO_USER_ID = 4_000_000_001L
        private const val APP_ID = 1_234_567L
        private const val OTHER_APP_ID = 7_654_321L
    }
}
