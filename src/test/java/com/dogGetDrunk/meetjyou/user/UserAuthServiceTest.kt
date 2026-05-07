package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.GeneratedRefreshToken
import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.auth.support.RefreshTokenFixtures
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.IncorrectJwtSubjectException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.terms.TermsService
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import java.time.LocalDateTime
import java.util.UUID

class UserAuthServiceTest : BehaviorSpec() {
    private val socialVerifierRegistry = mockk<SocialVerifierRegistry>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val userService = mockk<UserService>(relaxed = true)
    private val jwtProvider = mockk<JwtProvider>(relaxed = true)
    private val termsService = mockk<TermsService>(relaxed = true)
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)

    private val sut = UserAuthService(
        socialVerifierRegistry,
        userRepository,
        userService,
        jwtProvider,
        termsService,
        refreshTokenRepository,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach {
            clearAllMocks()
            every { refreshTokenRepository.save(any()) } answers { firstArg() }
        }
        afterSpec { unmockkAll() }

        // ── refreshToken ──────────────────────────────────────────────────────

        given("refreshToken 호출 시") {
            val user = UserFixtures.user()
            val jti = UUID.randomUUID().toString()
            val rawToken = "valid.refresh.token"
            val generatedRefreshToken = GeneratedRefreshToken(
                token = "new.refresh.token",
                jti = UUID.randomUUID(),
                expiresAt = LocalDateTime.now().plusDays(30),
            )

            `when`("유효한 토큰이고 DB 레코드가 정상이면") {
                then("기존 레코드를 revoke하고 새 TokenResponse를 반환한다") {
                    val record = RefreshTokenFixtures.refreshToken(user = user, jti = jti)

                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns record
                    every { jwtProvider.getUserUuid(rawToken) } returns user.uuid
                    every { jwtProvider.getUsername(rawToken) } returns user.email
                    every { userRepository.findByUuid(user.uuid) } returns user
                    every { jwtProvider.generateAccessToken(any(), any()) } returns "new.access.token"
                    every { jwtProvider.generateRefreshToken(any(), any()) } returns generatedRefreshToken

                    val result = sut.refreshToken(rawToken)

                    record.revoked shouldBe true
                    verify(exactly = 1) { refreshTokenRepository.save(any()) }
                    result.accessToken shouldBe "new.access.token"
                    result.refreshToken shouldBe "new.refresh.token"
                    result.uuid shouldBe user.uuid
                    result.email shouldBe user.email
                }
            }

            `when`("JWT 서명이 유효하지 않으면") {
                then("InvalidJwtException을 던진다") {
                    every { jwtProvider.validateToken(rawToken) } returns false

                    shouldThrow<InvalidJwtException> {
                        sut.refreshToken(rawToken)
                    }
                }
            }

            `when`("jti에 해당하는 DB 레코드가 없으면") {
                then("InvalidJwtException을 던진다") {
                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns null

                    shouldThrow<InvalidJwtException> {
                        sut.refreshToken(rawToken)
                    }
                }
            }

            `when`("DB 레코드가 revoked=true이면") {
                then("InvalidJwtException을 던진다") {
                    val revokedRecord = RefreshTokenFixtures.refreshToken(user = user, jti = jti, revoked = true)

                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns revokedRecord

                    shouldThrow<InvalidJwtException> {
                        sut.refreshToken(rawToken)
                    }
                }
            }

            `when`("DB 레코드가 만료되었으면") {
                then("InvalidJwtException을 던진다") {
                    val expiredRecord = RefreshTokenFixtures.refreshToken(
                        user = user,
                        jti = jti,
                        expiresAt = LocalDateTime.now().minusDays(1),
                    )

                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns expiredRecord

                    shouldThrow<InvalidJwtException> {
                        sut.refreshToken(rawToken)
                    }
                }
            }

            `when`("JWT email claim이 DB 유저의 email과 다르면") {
                then("IncorrectJwtSubjectException을 던진다") {
                    val record = RefreshTokenFixtures.refreshToken(user = user, jti = jti)

                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns record
                    every { jwtProvider.getUserUuid(rawToken) } returns user.uuid
                    every { jwtProvider.getUsername(rawToken) } returns "attacker@evil.com"
                    every { userRepository.findByUuid(user.uuid) } returns user

                    shouldThrow<IncorrectJwtSubjectException> {
                        sut.refreshToken(rawToken)
                    }
                }
            }

            `when`("JWT UUID에 해당하는 유저가 DB에 없으면") {
                then("UserNotFoundException을 던진다") {
                    val record = RefreshTokenFixtures.refreshToken(user = user, jti = jti)
                    val unknownUuid = UUID.randomUUID()

                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns record
                    every { jwtProvider.getUserUuid(rawToken) } returns unknownUuid
                    every { userRepository.findByUuid(unknownUuid) } returns null

                    shouldThrow<UserNotFoundException> {
                        sut.refreshToken(rawToken)
                    }
                }
            }
        }

        // ── logout ────────────────────────────────────────────────────────────

        given("logout 호출 시") {
            val jti = UUID.randomUUID().toString()
            val rawToken = "valid.refresh.token"
            val user = UserFixtures.user()

            `when`("유효한 토큰이고 DB 레코드가 존재하면") {
                then("레코드를 revoke하고 정상 종료한다") {
                    val record = RefreshTokenFixtures.refreshToken(user = user, jti = jti)

                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns record

                    sut.logout(rawToken)

                    record.revoked shouldBe true
                }
            }

            `when`("JWT 서명이 유효하지 않으면") {
                then("InvalidJwtException을 던진다") {
                    every { jwtProvider.validateToken(rawToken) } returns false

                    shouldThrow<InvalidJwtException> {
                        sut.logout(rawToken)
                    }
                }
            }

            `when`("jti에 해당하는 DB 레코드가 없으면") {
                then("InvalidJwtException을 던진다") {
                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns null

                    shouldThrow<InvalidJwtException> {
                        sut.logout(rawToken)
                    }
                }
            }
        }
    }
}
