package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.GeneratedRefreshToken
import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.auth.support.RefreshTokenFixtures
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.IncorrectJwtSubjectException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.user.UserAlreadyExistsException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.config.property.AdminProperties
import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.terms.TermsService
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
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
    private val userService = mockk<UserService>(relaxed = true)
    private val jwtProvider = mockk<JwtProvider>(relaxed = true)
    private val termsService = mockk<TermsService>(relaxed = true)
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)
    private val adminProperties = AdminProperties(claimPassphrase = "test-passphrase")
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)

    private val sut = UserAuthService(
        socialVerifierRegistry,
        userRepository,
        userService,
        jwtProvider,
        termsService,
        refreshTokenRepository,
        adminProperties,
        currentUserProvider,
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

                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns record
                    every { jwtProvider.getUserUuid(rawToken) } returns user.uuid
                    every { jwtProvider.getUsername(rawToken) } returns user.email
                    every { userRepository.findByUuid(user.uuid) } returns user
                    every { jwtProvider.generateAccessToken(any(), any(), any()) } returns "new.access.token"
                    every { jwtProvider.generateRefreshToken(any(), any()) } returns generatedRefreshToken

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

                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns revokedRecord
                    every { refreshTokenRepository.findByJti(replacementJti) } returns replacement
                    every { jwtProvider.getUserUuid(rawToken) } returns user.uuid
                    every { jwtProvider.getUsername(rawToken) } returns user.email
                    every { userRepository.findByUuid(user.uuid) } returns user
                    every { jwtProvider.generateAccessToken(any(), any(), any()) } returns "new.access.token"
                    every { jwtProvider.generateRefreshToken(any(), any()) } returns generatedRefreshToken

                    val result = sut.refreshToken(rawToken)

                    replacement.revoked shouldBe true
                    replacement.replacedByJti shouldBe generatedRefreshToken.jti.toString()
                    verify(exactly = 0) { refreshTokenRepository.revokeAllByUser(any()) }
                    result.refreshToken shouldBe "new.refresh.token"
                }
            }

            `when`("revoked이고 grace window를 벗어났으면") {
                then("InvalidJwtException을 던지고 전체 세션을 무효화한다") {
                    val revokedRecord = RefreshTokenFixtures.refreshToken(
                        user = user,
                        jti = jti,
                        revoked = true,
                        revokedAt = Instant.now().minusSeconds(60),
                        replacedByJti = UUID.randomUUID().toString(),
                    )

                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns revokedRecord

                    shouldThrow<InvalidJwtException> {
                        sut.refreshToken(rawToken)
                    }

                    verify(exactly = 1) { refreshTokenRepository.revokeAllByUser(user) }
                }
            }

            `when`("revoked이고 교체 토큰도 이미 소진됐으면(재사용 공격)") {
                then("InvalidJwtException을 던지고 전체 세션을 무효화한다") {
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

                    every { jwtProvider.validateToken(rawToken) } returns true
                    every { jwtProvider.getJti(rawToken) } returns jti
                    every { refreshTokenRepository.findByJti(jti) } returns revokedRecord
                    every { refreshTokenRepository.findByJti(replacementJti) } returns consumedReplacement

                    shouldThrow<InvalidJwtException> {
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

        // ── registerViaSocial ────────────────────────────────────────────────

        given("registerViaSocial 호출 시") {
            val provider = AuthProvider.KAKAO
            val externalId = "ext-register-1"
            val principal = SocialPrincipal(authProvider = provider, subject = externalId, email = "new@test.com")
            val socialVerifier = mockk<SocialVerifier>(relaxed = true)
            val request = RegistrationRequest(
                email = "new@test.com",
                nickname = "newbie",
                bio = null,
                gender = Gender.M,
                age = Age.TWENTY,
                personalities = emptyList(),
                travelStyles = emptyList(),
                diet = emptyList(),
                etc = emptyList(),
                authProvider = provider,
                idToken = "id-token-value",
                accessToken = null,
                agreedTermsUuids = emptyList(),
            )
            val generatedRefreshToken = GeneratedRefreshToken(
                token = "new.refresh.token",
                jti = UUID.randomUUID(),
                expiresAt = LocalDateTime.now().plusDays(30),
            )

            beforeEach {
                every { socialVerifierRegistry.get(provider) } returns socialVerifier
                every { socialVerifier.verifyAndExtract(IdToken("id-token-value"), any()) } returns principal
                every { jwtProvider.generateAccessToken(any(), any(), any()) } returns "new.access.token"
                every { jwtProvider.generateRefreshToken(any(), any()) } returns generatedRefreshToken
            }

            `when`("가입 이력이 없으면") {
                then("신규 유저를 생성하고 토큰을 발급한다") {
                    val newUser = UserFixtures.user(email = request.email, nickname = request.nickname, authProvider = provider, externalId = externalId)
                    every { userRepository.findByAuthProviderAndExternalId(provider, externalId) } returns null
                    every { userService.createUser(request, principal) } returns newUser

                    val result = sut.registerViaSocial(request)

                    result.refreshToken shouldBe "new.refresh.token"
                    verify(exactly = 1) { userService.createUser(request, principal) }
                    verify(exactly = 1) { termsService.saveUserTerms(newUser, any()) }
                }
            }

            `when`("grace window(30초) 이내에 생성된 동일 계정이 이미 존재하면(응답 유실 재시도)") {
                then("UserAlreadyExistsException 대신 그 유저의 로그인 토큰을 반환한다") {
                    val existingUser = UserFixtures.user(email = request.email, nickname = request.nickname, authProvider = provider, externalId = externalId)
                    every { userRepository.findByAuthProviderAndExternalId(provider, externalId) } returns existingUser

                    val result = sut.registerViaSocial(request)

                    result.uuid shouldBe existingUser.uuid
                    result.refreshToken shouldBe "new.refresh.token"
                    verify(exactly = 0) { userService.createUser(any(), any()) }
                    verify(exactly = 0) { termsService.saveUserTerms(any(), any()) }
                }
            }

            `when`("grace window(30초)를 벗어나 생성된 동일 계정이 이미 존재하면") {
                then("UserAlreadyExistsException을 던진다") {
                    val oldUser = UserFixtures.user(email = request.email, nickname = request.nickname, authProvider = provider, externalId = externalId)
                    forceCreatedAt(oldUser, Instant.now().minusSeconds(60))
                    every { userRepository.findByAuthProviderAndExternalId(provider, externalId) } returns oldUser

                    shouldThrow<UserAlreadyExistsException> {
                        sut.registerViaSocial(request)
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

    // User.createdAt is a @CreationTimestamp val with no setter; force it via reflection to
    // simulate an account created outside the registration retry grace window.
    private fun forceCreatedAt(user: User, instant: Instant) {
        val field = User::class.java.getDeclaredField("createdAt")
        field.isAccessible = true
        field.set(user, instant)
    }
}
