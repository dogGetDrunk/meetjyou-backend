package com.dogGetDrunk.meetjyou.common.filter

import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

/**
 * Documents the trust boundary AuthRateLimitFilter relies on: with
 * forward-headers-strategy=native, Tomcat's RemoteIpValve parses X-Forwarded-For
 * right-to-left and only trusts entries appended by a known-internal proxy IP.
 * nginx (see nginx/nginx.conf) uses $proxy_add_x_forwarded_for, which appends its
 * own observed remote address rather than forwarding the client's header verbatim -
 * that append is what actually prevents prefix-injection rate-limit bypass, not the
 * XFF header alone.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class XffTrustBoundaryTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Value("\${local.server.port}")
    private var port: Int = 0

    @MockBean
    private lateinit var firebaseApp: FirebaseApp

    @MockBean
    private lateinit var ociAuthProvider: AuthenticationDetailsProvider

    @MockBean
    private lateinit var objectStorageClient: ObjectStorageClient

    @MockBean
    private lateinit var workRequestClient: WorkRequestClient

    private fun postRegistrationWithXff(xff: String): Int {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("X-Forwarded-For", xff)
        val entity = HttpEntity("{}", headers)
        val response = restTemplate.postForEntity(
            "http://localhost:$port/api/v1/auth/registration",
            entity,
            String::class.java,
        )
        return response.statusCode.value()
    }

    init {
        extensions(SpringExtension())

        given("공격자가 매 요청마다 다른 값을 X-Forwarded-For 앞부분에 주입하되, 뒤에는 nginx가 append했을 실제 관측 IP가 고정으로 붙어있을 때") {
            `when`("registration 한도(5회/분)를 초과해서 요청하면") {
                then("가짜 앞부분과 무관하게 6번째 요청부터 429가 반환된다") {
                    val statuses = (1..6).map { i -> postRegistrationWithXff("$i.$i.$i.$i, 203.0.113.99") }

                    statuses.take(5).forEach { it shouldNotBe 429 }
                    statuses[5] shouldBe 429
                }
            }
        }

        given("(대조군) 프록시가 append 없이 클라이언트가 보낸 X-Forwarded-For 값을 그대로 넘긴다고 가정할 때") {
            `when`("매 요청마다 유일한 값 하나만 보내면") {
                then("매번 다른 IP로 인식되어 한도(5회)를 넘겨도 계속 통과한다") {
                    val statuses = (1..6).map { i -> postRegistrationWithXff("198.51.100.$i") }

                    statuses.forEach { it shouldNotBe 429 }
                }
            }
        }
    }
}
