package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.GeneratedRefreshToken
import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.common.exception.business.user.DuplicateNicknameException
import com.dogGetDrunk.meetjyou.common.exception.business.user.UserAlreadyExistsException
import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Gender
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class SocialRegistrationServiceTest : BehaviorSpec() {
    private val socialVerifierRegistry = mockk<SocialVerifierRegistry>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val userService = mockk<UserService>(relaxed = true)
    private val jwtProvider = mockk<JwtProvider>(relaxed = true)
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private val sut = SocialRegistrationService(
        socialVerifierRegistry,
        userRepository,
        userService,
        TokenPairIssuer(jwtProvider, refreshTokenRepository),
        TransactionTemplate(transactionManager),
        rotationOverlapSeconds = 30L,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        given("register 호출 시") {
            val provider = AuthProvider.KAKAO
            val externalId = "ext-register-1"
            val principal = SocialPrincipal(authProvider = provider, subject = externalId, email = "new@test.com")
            val socialVerifier = mockk<SocialVerifier>(relaxed = true)
            val request = RegistrationRequest(
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
                every { jwtProvider.generateAccessToken(any(), any()) } returns "new.access.token"
                every { jwtProvider.generateRefreshToken(any()) } returns generatedRefreshToken
                every { refreshTokenRepository.save(any()) } answers { firstArg() }
            }

            `when`("가입 이력이 없으면") {
                then("신규 유저를 생성하고 토큰을 발급한다") {
                    val newUser = UserFixtures.user(principal.email, request.nickname, provider, externalId)
                    every { userRepository.findBySocialAccount(provider, externalId) } returns null
                    every { userService.createUser(request, principal) } returns newUser

                    val result = sut.register(request)

                    result.uuid shouldBe newUser.uuid
                    result.refreshToken shouldBe "new.refresh.token"
                    verify(exactly = 1) { userService.createUser(request, principal) }
                }
            }

            `when`("grace window(30초) 이내에 생성된 동일 계정이 이미 존재하면(응답 유실 재시도)") {
                then("UserAlreadyExistsException 대신 그 유저의 로그인 토큰을 반환한다") {
                    val existingUser = UserFixtures.user(principal.email, request.nickname, provider, externalId)
                    every { userRepository.findBySocialAccount(provider, externalId) } returns existingUser

                    val result = sut.register(request)

                    result.uuid shouldBe existingUser.uuid
                    result.refreshToken shouldBe "new.refresh.token"
                    verify(exactly = 0) { userService.createUser(any(), any()) }
                }
            }

            `when`("grace window(30초)를 벗어나 생성된 동일 계정이 이미 존재하면") {
                then("UserAlreadyExistsException을 던진다") {
                    val oldUser = UserFixtures.user(principal.email, request.nickname, provider, externalId)
                    forceCreatedAt(oldUser, Instant.now().minusSeconds(60))
                    every { userRepository.findBySocialAccount(provider, externalId) } returns oldUser

                    shouldThrow<UserAlreadyExistsException> {
                        sut.register(request)
                    }
                }
            }

            `when`("같은 소셜 계정의 동시 요청(더블탭)에 져서 중복 예외가 나면") {
                then("새 트랜잭션에서 방금 만들어진 계정을 찾아 그 계정으로 로그인시킨다") {
                    val winner = UserFixtures.user(principal.email, request.nickname, provider, externalId)
                    // First lookup (before insert) sees nothing; the one after the lost race sees the winner.
                    every { userRepository.findBySocialAccount(provider, externalId) } returnsMany listOf(null, winner)
                    every { userService.createUser(request, principal) } throws
                        DuplicateNicknameException(request.nickname)

                    val result = sut.register(request)

                    result.uuid shouldBe winner.uuid
                    verify(exactly = 1) { transactionManager.rollback(any()) }
                    verify(exactly = 2) { transactionManager.getTransaction(any()) }
                }
            }

            `when`("다른 사람에게 닉네임을 빼앗겨 중복 예외가 나면") {
                then("같은 소셜 계정이 없으므로 닉네임 중복 예외를 그대로 던진다") {
                    every { userRepository.findBySocialAccount(provider, externalId) } returns null
                    every { userService.createUser(request, principal) } throws
                        DuplicateNicknameException(request.nickname)

                    shouldThrow<DuplicateNicknameException> {
                        sut.register(request)
                    }
                    verify(exactly = 0) { refreshTokenRepository.save(any()) }
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
