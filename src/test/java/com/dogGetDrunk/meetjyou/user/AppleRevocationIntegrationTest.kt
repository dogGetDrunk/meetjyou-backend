package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleAuthApiException
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleAuthClient
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleTokenResponse
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
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
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate

/**
 * Withdrawal and Apple unlink through real HTTP and the filter chain, with only the Apple REST
 * calls and id_token signature check mocked: the header reaches the revoker, the DB change always
 * lands, and an Apple failure never turns the user's request into an error.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
// Revocation switched on; the key is never parsed because AppleAuthClient is mocked.
@TestPropertySource(
    properties = [
        "auth.apple.team-id=TEAMID1234",
        "auth.apple.key-id=KEYID56789",
        "auth.apple.private-key=unused-in-this-test",
    ],
)
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class AppleRevocationIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @MockBean
    private lateinit var appleAuthClient: AppleAuthClient

    @MockBean(name = "appleJwtDecoder")
    private lateinit var appleJwtDecoder: JwtDecoder

    @Value("\${local.server.port}")
    private var port: Int = 0

    private var seq = 0

    private fun url(path: String) = "http://localhost:$port/api/v1$path"

    private fun saveUser(vararg providers: AuthProvider): User {
        seq++
        val user = User(email = null, nickname = "ar$seq", authProvider = providers.first(), externalId = "ar-ext-$seq")
        providers.forEach { user.addSocialAccount(it, subjectOf(it), null) }
        return userRepository.save(user)
    }

    private fun subjectOf(provider: AuthProvider) = "${provider.name.lowercase()}-sub-$seq"

    private fun delete(path: String, user: User, appleCode: String?): ResponseEntity<String> {
        val headers = HttpHeaders().apply {
            setBearerAuth(jwtProvider.generateAccessToken(user.uuid, Role.USER))
            appleCode?.let { set(AccountExitService.APPLE_AUTHORIZATION_CODE_HEADER, it) }
        }
        return restTemplate.exchange(url(path), HttpMethod.DELETE, HttpEntity<Void>(headers), String::class.java)
    }

    private fun stubAppleExchange(subject: String) {
        `when`(appleAuthClient.exchangeCode(APPLE_CODE))
            .thenReturn(AppleTokenResponse(accessToken = "access-1", refreshToken = REFRESH_TOKEN, idToken = ID_TOKEN))
        `when`(appleJwtDecoder.decode(ID_TOKEN))
            .thenReturn(Jwt.withTokenValue(ID_TOKEN).header("alg", "RS256").subject(subject).build())
    }

    private fun reload(user: User): User = userRepository.findById(user.id).orElseThrow()

    private fun linkedProviders(user: User): List<AuthProvider> = transactionTemplate.execute {
        reload(user).socialAccounts.map { it.provider }
    } ?: emptyList()

    init {
        extensions(SpringExtension())

        beforeEach { reset(appleAuthClient, appleJwtDecoder) }
        afterEach { userRepository.deleteAll() }

        given("애플 회원이 탈퇴 요청에 재인증 code를 실어 보내면") {
            `when`("애플이 정상 응답하면") {
                then("200으로 탈퇴 처리하고 애플 연동도 해제한다") {
                    val user = saveUser(AuthProvider.APPLE)
                    stubAppleExchange(subject = subjectOf(AuthProvider.APPLE))

                    val response = delete("/users/me", user, APPLE_CODE)

                    response.statusCode.value() shouldBe HttpStatus.OK.value()
                    reload(user).status shouldBe UserStatus.DELETED
                    verify(appleAuthClient).revoke(REFRESH_TOKEN, AppleAuthClient.TOKEN_TYPE_HINT_REFRESH)
                }
            }

            `when`("애플이 장애로 실패하면") {
                then("그래도 200으로 탈퇴는 완료한다") {
                    val user = saveUser(AuthProvider.APPLE)
                    `when`(appleAuthClient.exchangeCode(anyString()))
                        .thenThrow(AppleAuthApiException(AppleAuthApiException.Reason.UNAVAILABLE, "down"))

                    val response = delete("/users/me", user, APPLE_CODE)

                    response.statusCode.value() shouldBe HttpStatus.OK.value()
                    reload(user).status shouldBe UserStatus.DELETED
                }
            }
        }

        given("구버전 앱이 code 없이 탈퇴를 요청하면") {
            `when`("헤더가 없어도") {
                then("탈퇴는 그대로 성공하고 애플은 호출하지 않는다") {
                    val user = saveUser(AuthProvider.APPLE)

                    val response = delete("/users/me", user, appleCode = null)

                    response.statusCode.value() shouldBe HttpStatus.OK.value()
                    reload(user).status shouldBe UserStatus.DELETED
                    verify(appleAuthClient, never()).exchangeCode(anyString())
                }
            }
        }

        given("카카오·애플이 연결된 회원이 애플 연결을 해제하면") {
            `when`("재인증 code를 실어 보내면") {
                then("204로 애플 계정을 떼고 애플 연동도 해제한다") {
                    val user = saveUser(AuthProvider.KAKAO, AuthProvider.APPLE)
                    stubAppleExchange(subject = subjectOf(AuthProvider.APPLE))

                    val response = delete("/users/me/social-accounts/APPLE", user, APPLE_CODE)

                    response.statusCode.value() shouldBe HttpStatus.NO_CONTENT.value()
                    linkedProviders(user) shouldContainExactly listOf(AuthProvider.KAKAO)
                    verify(appleAuthClient).revoke(REFRESH_TOKEN, AppleAuthClient.TOKEN_TYPE_HINT_REFRESH)
                }
            }
        }

        given("애플만 연결된 회원이 애플 연결을 해제하면") {
            `when`("마지막 계정이라 409로 거절되면") {
                then("요청이 실패했으니 애플 연동도 건드리지 않는다") {
                    val user = saveUser(AuthProvider.APPLE)

                    val response = delete("/users/me/social-accounts/APPLE", user, APPLE_CODE)

                    response.statusCode.value() shouldBe HttpStatus.CONFLICT.value()
                    verify(appleAuthClient, never()).exchangeCode(anyString())
                }
            }
        }
    }

    private companion object {
        const val APPLE_CODE = "fresh-apple-code"
        const val ID_TOKEN = "apple.id.token"
        const val REFRESH_TOKEN = "apple-refresh-1"
    }
}
