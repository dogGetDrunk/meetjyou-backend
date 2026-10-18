package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.GeneratedRefreshToken
import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.auth.support.RefreshTokenFixtures
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.IncorrectJwtSubjectException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.RefreshTokenReuseDetectedException
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
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class UserAuthServiceTest : BehaviorSpec() {
    private val socialVerifierRegistry = mockk<SocialVerifierRegistry>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val jwtProvider = mockk<JwtProvider>(relaxed = true)
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)

    private val sut = UserAuthService(
        socialVerifierRegistry,
        userRepository,
        jwtProvider,
        refreshTokenRepository,
        TokenPairIssuer(jwtProvider, refreshTokenRepository),
        rotationOverlapSeconds = 30L,
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

                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns record
                    every { jwtProvider.getUserUuid(rawToken) } returns user.uuid
                    every { jwtProvider.generateAccessToken(any(), any()) } returns "new.access.token"
                    every { jwtProvider.generateRefreshToken(any()) } returns generatedRefreshToken

                    val result = sut.refreshToken(rawToken)

                    record.revoked shouldBe true
                    verify(exactly = 1) { refreshTokenRepository.save(any()) }
                    result.accessToken shouldBe "new.access.token"
                    result.refreshToken shouldBe "new.refresh.token"
                    result.uuid shouldBe user.uuid
                }
            }

            `when`("JWT 서명이 유효하지 않으면") {
                then("InvalidJwtException을 던진다") {
                    every { jwtProvider.isRefreshToken(rawToken) } returns false

                    shouldThrow<InvalidJwtException> {
                        sut.refreshToken(rawToken)
                    }
                }
            }

            `when`("jti에 해당하는 DB 레코드가 없으면") {
                then("InvalidJwtException을 던진다") {
                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns null

                    shouldThrow<InvalidJwtException> {
                        sut.refreshToken(rawToken)
                    }
                }
            }

            `when`("DB 레코드가 revoked=true이면") {
                then("RefreshTokenReuseDetectedException을 던진다") {
                    val revokedRecord = RefreshTokenFixtures.refreshToken(user = user, jti = jti, revoked = true)

                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns revokedRecord

                    shouldThrow<RefreshTokenReuseDetectedException> {
                        sut.refreshToken(rawToken)
                    }
                }
            }

            `when`("revoked이지만 grace window 안의 최신 교체 토큰(N-1)이면") {
                then("교체 토큰을 기준으로 회전하고 새 TokenResponse를 반환한다") {
                    val replacementJti = UUID.randomUUID().toString()
                    val replacement = RefreshTokenFixtures.refreshToken(user = user, jti = replacementJti)
                    val revokedRecord = RefreshTokenFixtures.refreshToken(
                        user = user,
                        jti = jti,
                        revoked = true,
                        revokedAt = Instant.now().minusSeconds(5),
                        replacedByJti = replacementJti,
                    )

                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns revokedRecord
                    every { refreshTokenRepository.findForUpdateByJti(replacementJti) } returns replacement
                    every { jwtProvider.getUserUuid(rawToken) } returns user.uuid
                    every { jwtProvider.generateAccessToken(any(), any()) } returns "new.access.token"
                    every { jwtProvider.generateRefreshToken(any()) } returns generatedRefreshToken

                    val result = sut.refreshToken(rawToken)

                    replacement.revoked shouldBe true
                    replacement.replacedByJti shouldBe generatedRefreshToken.jti.toString()
                    verify(exactly = 0) { refreshTokenRepository.revokeAllByUser(any()) }
                    result.refreshToken shouldBe "new.refresh.token"
                }
            }

            `when`("revoked이고 grace window를 벗어났으면") {
                then("RefreshTokenReuseDetectedException을 던지고 전체 세션을 무효화한다") {
                    val revokedRecord = RefreshTokenFixtures.refreshToken(
                        user = user,
                        jti = jti,
                        revoked = true,
                        revokedAt = Instant.now().minusSeconds(60),
                        replacedByJti = UUID.randomUUID().toString(),
                    )

                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns revokedRecord

                    shouldThrow<RefreshTokenReuseDetectedException> {
                        sut.refreshToken(rawToken)
                    }

                    verify(exactly = 1) { refreshTokenRepository.revokeAllByUser(user) }
                }
            }

            `when`("revoked이고 교체 토큰도 이미 소진됐으면(재사용 공격)") {
                then("RefreshTokenReuseDetectedException을 던지고 전체 세션을 무효화한다") {
                    val replacementJti = UUID.randomUUID().toString()
                    val consumedReplacement = RefreshTokenFixtures.refreshToken(
                        user = user,
                        jti = replacementJti,
                        revoked = true,
                    )
                    val revokedRecord = RefreshTokenFixtures.refreshToken(
                        user = user,
                        jti = jti,
                        revoked = true,
                        revokedAt = Instant.now().minusSeconds(5),
                        replacedByJti = replacementJti,
                    )

                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns revokedRecord
                    every { refreshTokenRepository.findForUpdateByJti(replacementJti) } returns consumedReplacement

                    shouldThrow<RefreshTokenReuseDetectedException> {
                        sut.refreshToken(rawToken)
                    }

                    verify(exactly = 1) { refreshTokenRepository.revokeAllByUser(user) }
                }
            }

            `when`("DB 레코드가 만료되었으면") {
                then("InvalidJwtException을 던진다") {
                    val expiredRecord = RefreshTokenFixtures.refreshToken(
                        user = user,
                        jti = jti,
                        expiresAt = LocalDateTime.now().minusDays(1),
                    )

                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns expiredRecord

                    shouldThrow<InvalidJwtException> {
                        sut.refreshToken(rawToken)
                    }
                }
            }

            `when`("JWT userUuid claim이 refresh 레코드 소유자와 다르면") {
                then("IncorrectJwtSubjectException을 던지고 토큰을 발급하지 않는다") {
                    val record = RefreshTokenFixtures.refreshToken(user = user, jti = jti)
                    val otherUuid = UUID.randomUUID()

                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns record
                    every { jwtProvider.getUserUuid(rawToken) } returns otherUuid

                    shouldThrow<IncorrectJwtSubjectException> {
                        sut.refreshToken(rawToken)
                    }
                    verify(exactly = 0) { refreshTokenRepository.save(any()) }
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

                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns record

                    sut.logout(rawToken)

                    record.revoked shouldBe true
                }
            }

            `when`("이미 다음 토큰으로 회전된 토큰이면") {
                then("체인 끝의 토큰까지 따라가 폐기하고 기존 연결은 건드리지 않는다") {
                    val headJti = UUID.randomUUID().toString()
                    val head = RefreshTokenFixtures.refreshToken(user = user, jti = headJti)
                    val revokedAt = Instant.now().minusSeconds(5)
                    val rotated = RefreshTokenFixtures.refreshToken(
                        user = user,
                        jti = jti,
                        revoked = true,
                        revokedAt = revokedAt,
                        replacedByJti = headJti,
                    )

                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns rotated
                    every { refreshTokenRepository.findForUpdateByJti(headJti) } returns head

                    sut.logout(rawToken)

                    head.revoked shouldBe true
                    rotated.replacedByJti shouldBe headJti
                    rotated.revokedAt shouldBe revokedAt
                }
            }

            `when`("이미 로그아웃된(교체 없이 폐기된) 토큰이면") {
                then("아무것도 바꾸지 않고 정상 종료한다") {
                    val revokedAt = Instant.now().minusSeconds(60)
                    val loggedOut = RefreshTokenFixtures.refreshToken(
                        user = user,
                        jti = jti,
                        revoked = true,
                        revokedAt = revokedAt,
                    )

                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns loggedOut

                    sut.logout(rawToken)

                    loggedOut.revokedAt shouldBe revokedAt
                    verify(exactly = 0) { refreshTokenRepository.revokeAllByUser(any()) }
                }
            }

            `when`("체인이 상한보다 길게 이어지면") {
                then("체인 끝을 못 찾은 것으로 보고 그 유저의 세션을 모두 폐기한다") {
                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    // Every lookup returns another already-rotated token, so the chain never ends.
                    every { refreshTokenRepository.findForUpdateByJti(any()) } answers {
                        RefreshTokenFixtures.refreshToken(
                            user = user,
                            jti = firstArg(),
                            revoked = true,
                            replacedByJti = UUID.randomUUID().toString(),
                        )
                    }

                    sut.logout(rawToken)

                    verify(exactly = 1) { refreshTokenRepository.revokeAllByUser(user) }
                }
            }

            `when`("JWT 서명이 유효하지 않으면") {
                then("InvalidJwtException을 던진다") {
                    every { jwtProvider.isRefreshToken(rawToken) } returns false

                    shouldThrow<InvalidJwtException> {
                        sut.logout(rawToken)
                    }
                }
            }

            `when`("jti에 해당하는 DB 레코드가 없으면") {
                then("InvalidJwtException을 던진다") {
                    every { jwtProvider.isRefreshToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findForUpdateByJti(jti) } returns null

                    shouldThrow<InvalidJwtException> {
                        sut.logout(rawToken)
                    }
                }
            }
        }
    }
}
