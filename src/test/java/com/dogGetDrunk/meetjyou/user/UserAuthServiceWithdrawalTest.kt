package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshToken
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.UserWithdrawnException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.config.property.AdminProperties
import com.dogGetDrunk.meetjyou.terms.TermsService
import com.dogGetDrunk.meetjyou.user.dto.LoginRequest
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime

class UserAuthServiceWithdrawalTest : BehaviorSpec() {

    private val socialVerifierRegistry = mockk<SocialVerifierRegistry>()
    private val socialVerifier = mockk<SocialVerifier>()
    private val userRepository = mockk<UserRepository>()
    private val userService = mockk<UserService>(relaxed = true)
    private val jwtProvider = mockk<JwtProvider>(relaxed = true)
    private val termsService = mockk<TermsService>(relaxed = true)
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)
    private val adminProperties = mockk<AdminProperties>(relaxed = true)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val sut = UserAuthService(
        socialVerifierRegistry, userRepository, userService, jwtProvider,
        termsService, refreshTokenRepository, adminProperties, currentUserProvider,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("탈퇴(DELETED) 처리된 계정으로") {
            val withdrawnUser = UserFixtures.user().apply { status = UserStatus.DELETED }

            `when`("소셜 로그인을 시도하면") {
                then("UserWithdrawnException을 던지고 토큰을 발급하지 않는다") {
                    val principal = SocialPrincipal(withdrawnUser.authProvider, withdrawnUser.externalId, withdrawnUser.email)
                    every { socialVerifierRegistry.get(withdrawnUser.authProvider) } returns socialVerifier
                    every { socialVerifier.verifyAndExtract(IdToken("id-token"), any()) } returns principal
                    every {
                        userRepository.findByAuthProviderAndExternalId(withdrawnUser.authProvider, withdrawnUser.externalId)
                    } returns withdrawnUser

                    val request = LoginRequest(authProvider = withdrawnUser.authProvider, idToken = "id-token")

                    shouldThrow<UserWithdrawnException> {
                        sut.loginViaSocial(request, nonce = "test-nonce")
                    }
                }
            }

            `when`("리프레시 토큰으로 재발급을 시도하면") {
                then("UserWithdrawnException을 던지고 토큰을 재발급하지 않는다") {
                    val record = RefreshToken(jti = "jti-1", user = withdrawnUser, expiresAt = LocalDateTime.now().plusDays(1))
                    every { jwtProvider.validateToken("raw-refresh-token") } returns true
                    every { jwtProvider.getJti("raw-refresh-token") } returns "jti-1"
                    every { refreshTokenRepository.findByJti("jti-1") } returns record
                    every { jwtProvider.getUserUuid("raw-refresh-token") } returns withdrawnUser.uuid
                    every { jwtProvider.getUsername("raw-refresh-token") } returns withdrawnUser.email
                    every { userRepository.findByUuid(withdrawnUser.uuid) } returns withdrawnUser

                    shouldThrow<UserWithdrawnException> {
                        sut.refreshToken("raw-refresh-token")
                    }
                }
            }
        }
    }
}
