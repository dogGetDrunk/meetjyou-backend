package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshToken
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.UserWithdrawnException
import com.dogGetDrunk.meetjyou.terms.TermsService
import com.dogGetDrunk.meetjyou.user.dto.LoginRequest
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class UserAuthServiceWithdrawalTest : BehaviorSpec() {

    private val socialVerifierRegistry = mockk<SocialVerifierRegistry>()
    private val socialVerifier = mockk<SocialVerifier>()
    private val userRepository = mockk<UserRepository>()
    private val userService = mockk<UserService>(relaxed = true)
    private val jwtProvider = mockk<JwtProvider>(relaxed = true)
    private val termsService = mockk<TermsService>(relaxed = true)
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)
    private val sut = UserAuthService(
        socialVerifierRegistry, userRepository, userService, jwtProvider,
        termsService, refreshTokenRepository,
        rotationOverlapSeconds = 30L,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun stubLogin(user: User) {
        val principal = SocialPrincipal(user.authProvider, user.externalId, user.email)
        every { socialVerifierRegistry.get(user.authProvider) } returns socialVerifier
        every { socialVerifier.verifyAndExtract(IdToken(ID_TOKEN), any()) } returns principal
        every { userRepository.findBySocialAccount(user.authProvider, user.externalId) } returns user
    }

    private fun loginRequest(user: User) = LoginRequest(authProvider = user.authProvider, idToken = ID_TOKEN)

    companion object {
        private const val ID_TOKEN = "id-token"
    }

    init {
        beforeEach { clearAllMocks() }

        given("유예기간(30일)이 지난 탈퇴 계정으로") {
            val withdrawnUser = UserFixtures.user().apply { withdraw(Instant.now().minus(Duration.ofDays(31))) }

            `when`("소셜 로그인을 시도하면") {
                then("UserWithdrawnException을 던지고 계정을 복구하지 않는다") {
                    stubLogin(withdrawnUser)

                    shouldThrow<UserWithdrawnException> {
                        sut.loginViaSocial(loginRequest(withdrawnUser), nonce = "test-nonce")
                    }
                    withdrawnUser.status shouldBe UserStatus.DELETED
                }
            }
        }

        given("유예기간 안의 탈퇴 계정으로") {
            val withdrawnUser = UserFixtures.user().apply { withdraw(Instant.now().minus(Duration.ofDays(29))) }

            `when`("소셜 로그인을 시도하면") {
                then("계정을 NORMAL로 복구하고 토큰을 발급한다") {
                    stubLogin(withdrawnUser)
                    every { refreshTokenRepository.save(any()) } answers { firstArg() }

                    val response = sut.loginViaSocial(loginRequest(withdrawnUser), nonce = "test-nonce")

                    response.uuid shouldBe withdrawnUser.uuid
                    withdrawnUser.status shouldBe UserStatus.NORMAL
                    withdrawnUser.withdrawnAt shouldBe null
                }
            }

            `when`("리프레시 토큰으로 재발급을 시도하면") {
                then("UserWithdrawnException을 던지고 토큰을 재발급하지 않는다") {
                    val record = RefreshToken(jti = "jti-1", user = withdrawnUser, expiresAt = LocalDateTime.now().plusDays(1))
                    every { jwtProvider.isRefreshToken("raw-refresh-token") } returns true
                    every { jwtProvider.getJti("raw-refresh-token") } returns "jti-1"
                    every { refreshTokenRepository.findByJti("jti-1") } returns record
                    every { jwtProvider.getUserUuid("raw-refresh-token") } returns withdrawnUser.uuid
                    every { userRepository.findByUuid(withdrawnUser.uuid) } returns withdrawnUser

                    shouldThrow<UserWithdrawnException> {
                        sut.refreshToken("raw-refresh-token")
                    }
                }
            }
        }
    }
}
