package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.support.EXTERNAL_CALL_H2_URL
import com.dogGetDrunk.meetjyou.support.ExternalCallTestConfig
import com.dogGetDrunk.meetjyou.support.VerifierProbe
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * AT-001 (login) and AT-002 (social link): for every provider the verifier runs outside any
 * transaction and with no pooled connection held by the request.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=$EXTERNAL_CALL_H2_URL"],
)
@ActiveProfiles("test")
@Import(ExternalCallTestConfig::class)
@MockBean(
    FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class,
)
class SocialAuthConnectionIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var rest: TestRestTemplate
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var probe: VerifierProbe

    private fun seedUser(provider: AuthProvider, externalId: String): User {
        val tag = UUID.randomUUID().toString().take(NICKNAME_LENGTH)
        val user = User(null, tag, provider, externalId)
        user.addSocialAccount(provider, externalId, null)
        return users.save(user)
    }

    private fun jsonHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return headers
    }

    private fun tokenBody(provider: AuthProvider) = mapOf("authProvider" to provider.name, "accessToken" to "token")

    private fun login(provider: AuthProvider): ResponseEntity<String> =
        rest.postForEntity("/api/v1/auth/login", HttpEntity(tokenBody(provider), jsonHeaders()), String::class.java)

    private fun link(user: User, provider: AuthProvider): ResponseEntity<String> {
        val headers = jsonHeaders()
        headers.setBearerAuth(jwt.generateAccessToken(user.uuid, user.role))
        val request = HttpEntity(tokenBody(provider), headers)
        return rest.postForEntity("/api/v1/users/me/social-accounts", request, String::class.java)
    }

    private fun otherThan(provider: AuthProvider) =
        if (provider == AuthProvider.KAKAO) AuthProvider.GOOGLE else AuthProvider.KAKAO

    private fun assertSingleCleanCall(provider: AuthProvider) {
        probe.observations shouldHaveSize 1
        val observation = probe.observations.single()
        observation.provider shouldBe provider
        observation.transactionActive shouldBe false
        observation.activeConnections shouldBe 0
    }

    init {
        extensions(SpringExtension())

        beforeEach {
            probe.observations.clear()
            probe.subject = UUID.randomUUID().toString()
        }

        PROVIDERS.forEach { provider ->
            given("$provider 로그인") {
                then("검증기 호출 중 트랜잭션도 커넥션도 없고 200으로 토큰을 발급한다") {
                    seedUser(provider, probe.subject)

                    val response = login(provider)

                    assertSingleCleanCall(provider)
                    response.statusCode shouldBe HttpStatus.OK
                }
            }

            given("$provider 소셜 연결") {
                then("검증기 호출 중 트랜잭션도 커넥션도 없고 새 연결 201, 재시도 200") {
                    val user = seedUser(otherThan(provider), UUID.randomUUID().toString())

                    val created = link(user, provider)
                    assertSingleCleanCall(provider)
                    created.statusCode shouldBe HttpStatus.CREATED

                    probe.observations.clear()
                    val retried = link(user, provider)
                    assertSingleCleanCall(provider)
                    retried.statusCode shouldBe HttpStatus.OK
                }
            }
        }
    }

    private companion object {
        const val NICKNAME_LENGTH = 10
        val PROVIDERS = listOf(AuthProvider.KAKAO, AuthProvider.GOOGLE, AuthProvider.APPLE)
    }
}
