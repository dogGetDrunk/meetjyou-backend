package com.dogGetDrunk.meetjyou.auth.social.apple

import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.io.IOException
import java.net.URLDecoder
import java.security.interfaces.ECPublicKey

class AppleAuthClientTest : BehaviorSpec() {

    // Rebuilt in beforeEach: one spec instance can run several leaves and the mock server is stateful.
    private lateinit var appleApi: MockRestServiceServer
    private lateinit var sut: AppleAuthClient

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun formOf(request: ClientHttpRequest): Map<String, String> =
        (request as MockClientHttpRequest).bodyAsString.split("&").associate {
            val (key, value) = it.split("=", limit = 2)
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }

    init {
        beforeEach {
            val builder = RestClient.builder()
            appleApi = MockRestServiceServer.bindTo(builder).build()
            val props = AppleTestKeys.props()
            sut = AppleAuthClient(props, AppleClientSecretFactory(props), builder.build())
        }

        given("exchangeCode 호출 시") {
            `when`("애플이 토큰을 내주면") {
                then("client_id·서명된 client_secret·code·grant_type을 form으로 보내고 응답을 파싱한다") {
                    var sentForm = emptyMap<String, String>()
                    appleApi.expect(requestTo(AppleAuthClient.TOKEN_URI))
                        .andExpect(method(HttpMethod.POST))
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                        .andExpect { sentForm = formOf(it) }
                        .andRespond(withSuccess(TOKEN_RESPONSE_JSON, MediaType.APPLICATION_JSON))

                    val tokens = sut.exchangeCode("auth-code-1")

                    tokens.accessToken shouldBe "access-1"
                    tokens.refreshToken shouldBe "refresh-1"
                    tokens.idToken shouldBe "id.token.value"
                    sentForm["client_id"] shouldBe AppleTestKeys.CLIENT_ID
                    sentForm["code"] shouldBe "auth-code-1"
                    sentForm["grant_type"] shouldBe "authorization_code"
                    val secret = SignedJWT.parse(sentForm["client_secret"])
                    secret.verify(ECDSAVerifier(AppleTestKeys.keyPair.public as ECPublicKey)) shouldBe true
                    appleApi.verify()
                }
            }

            `when`("응답에 refresh_token이 없으면") {
                then("refreshToken을 null로 둔다") {
                    appleApi.expect(requestTo(AppleAuthClient.TOKEN_URI))
                        .andRespond(withSuccess(TOKEN_RESPONSE_WITHOUT_REFRESH_JSON, MediaType.APPLICATION_JSON))

                    sut.exchangeCode("auth-code-1").refreshToken.shouldBeNull()
                }
            }

            `when`("code가 만료·재사용돼 애플이 400 invalid_grant로 거절하면") {
                then("REJECTED 사유로 던진다") {
                    appleApi.expect(requestTo(AppleAuthClient.TOKEN_URI))
                        .andRespond(
                            withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""{"error":"invalid_grant"}"""),
                        )

                    val ex = shouldThrow<AppleAuthApiException> { sut.exchangeCode("used-code") }

                    ex.reason shouldBe AppleAuthApiException.Reason.REJECTED
                }
            }

            `when`("애플이 5xx로 응답하면") {
                then("UNAVAILABLE 사유로 던진다") {
                    appleApi.expect(requestTo(AppleAuthClient.TOKEN_URI))
                        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

                    val ex = shouldThrow<AppleAuthApiException> { sut.exchangeCode("auth-code-1") }

                    ex.reason shouldBe AppleAuthApiException.Reason.UNAVAILABLE
                }
            }

            `when`("애플에 닿지 않으면 (timeout·연결 거부)") {
                then("UNAVAILABLE 사유로 던진다") {
                    appleApi.expect(requestTo(AppleAuthClient.TOKEN_URI))
                        .andRespond(withException(IOException("connect timed out")))

                    val ex = shouldThrow<AppleAuthApiException> { sut.exchangeCode("auth-code-1") }

                    ex.reason shouldBe AppleAuthApiException.Reason.UNAVAILABLE
                }
            }
        }

        given("revoke 호출 시") {
            `when`("토큰과 종류 힌트를 넘기면") {
                then("revoke 엔드포인트에 client 인증과 함께 form으로 보낸다") {
                    var sentForm = emptyMap<String, String>()
                    appleApi.expect(requestTo(AppleAuthClient.REVOKE_URI))
                        .andExpect(method(HttpMethod.POST))
                        .andExpect { sentForm = formOf(it) }
                        .andRespond(withSuccess())

                    sut.revoke("refresh-1", AppleAuthClient.TOKEN_TYPE_HINT_REFRESH)

                    sentForm["client_id"] shouldBe AppleTestKeys.CLIENT_ID
                    sentForm["token"] shouldBe "refresh-1"
                    sentForm["token_type_hint"] shouldBe "refresh_token"
                    (sentForm["client_secret"] != null) shouldBe true
                    appleApi.verify()
                }
            }

            `when`("애플이 거절하면 (예: client_secret 오류 400 invalid_client)") {
                then("REJECTED 사유로 던진다") {
                    appleApi.expect(requestTo(AppleAuthClient.REVOKE_URI))
                        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("""{"error":"invalid_client"}"""))

                    val ex = shouldThrow<AppleAuthApiException> {
                        sut.revoke("refresh-1", AppleAuthClient.TOKEN_TYPE_HINT_REFRESH)
                    }

                    ex.reason shouldBe AppleAuthApiException.Reason.REJECTED
                }
            }
        }
    }

    private companion object {
        const val TOKEN_RESPONSE_JSON = """
            {"access_token":"access-1","token_type":"Bearer","expires_in":3600,
             "refresh_token":"refresh-1","id_token":"id.token.value"}
        """
        const val TOKEN_RESPONSE_WITHOUT_REFRESH_JSON = """
            {"access_token":"access-1","token_type":"Bearer","expires_in":3600,"id_token":"id.token.value"}
        """
    }
}
