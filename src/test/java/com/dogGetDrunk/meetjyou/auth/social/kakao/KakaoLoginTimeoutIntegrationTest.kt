package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

private const val H2_URL = "jdbc:h2:mem:kakao_login_timeout;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE"

/**
 * AT-003 (HTTP): with the real KakaoVerifier, a Kakao endpoint that accepts the request and holds
 * its response makes POST /auth/login answer 503 SOCIAL_PROVIDER_UNAVAILABLE after the read timeout.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=$H2_URL"],
)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class,
)
class KakaoLoginTimeoutIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var rest: TestRestTemplate
    @Autowired private lateinit var mapper: ObjectMapper

    init {
        extensions(SpringExtension())

        afterSpec { server.close() }

        given("Kakao가 access token 확인 요청을 받고 응답을 보류하면") {
            then("로그인은 read timeout 뒤 503 SOCIAL_PROVIDER_UNAVAILABLE") {
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val body = mapOf("authProvider" to "KAKAO", "accessToken" to "held-token")

                val response = rest.postForEntity("/api/v1/auth/login", HttpEntity(body, headers), String::class.java)

                server.received.count shouldBe 0L
                response.statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
                mapper.readTree(response.body)["errorCode"].asText() shouldBe "SOCIAL_PROVIDER_UNAVAILABLE"
            }
        }
    }

    companion object {
        private const val READ_TIMEOUT = "200ms"
        private val server = HeldResponseServer()

        @JvmStatic
        @DynamicPropertySource
        fun kakaoEndpoints(registry: DynamicPropertyRegistry) {
            registry.add("auth.kakao.access-token-info-uri") { "${server.baseUrl}/v1/user/access_token_info" }
            registry.add("auth.kakao.user-info-uri") { "${server.baseUrl}/v2/user/me" }
            registry.add("auth.kakao.read-timeout") { READ_TIMEOUT }
        }
    }
}
