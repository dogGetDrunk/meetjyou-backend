package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.user.dto.DevRegisterRequest
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.UUID

class DevUserAuthServiceTest : BehaviorSpec() {
    // given/when 블록 본문은 spec 초기화 시점에 실행되므로,
    // mock은 class-level val로 선언하고 beforeEach로 상태만 초기화한다.
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val jwtProvider: JwtProvider = mockk(relaxed = true)
    private val sut = DevUserAuthService(userRepository, jwtProvider)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        // ── registerOrLogin ──────────────────────────────────────────────────

        given("registerOrLogin 호출 시") {
            `when`("해당 이메일의 유저가 DB에 없으면") {
                then("신규 유저를 생성하고 TokenResponse를 반환한다") {
                    val email = "new@local.dev"
                    val request = DevRegisterRequest(email = email, nickname = "신규유저")
                    val newUser = UserFixtures.user(email = email, nickname = "신규유저")
                    val capturedUser = slot<User>()

                    every { userRepository.findByEmail(email) } returns null
                    every { userRepository.save(capture(capturedUser)) } returns newUser
                    every { jwtProvider.generateAccessToken(any(), any()) } returns "access-token"
                    every { jwtProvider.generateRefreshToken(any(), any()) } returns "refresh-token"

                    val result = sut.registerOrLogin(request)

                    verify(exactly = 1) { userRepository.save(any()) }
                    capturedUser.captured.email shouldBe email
                    capturedUser.captured.authProvider shouldBe AuthProvider.KAKAO
                    capturedUser.captured.externalId shouldBe "dev-$email"
                    result.uuid shouldBe newUser.uuid
                    result.email shouldBe newUser.email
                    result.accessToken shouldBe "access-token"
                    result.refreshToken shouldBe "refresh-token"
                }
            }

            `when`("해당 이메일의 유저가 이미 DB에 있으면") {
                then("기존 유저로 TokenResponse를 반환하고 save를 호출하지 않는다") {
                    val email = "existing@local.dev"
                    val request = DevRegisterRequest(email = email, nickname = "기존유저")
                    val existingUser = UserFixtures.user(email = email, nickname = "기존유저")

                    every { userRepository.findByEmail(email) } returns existingUser
                    every { jwtProvider.generateAccessToken(existingUser.uuid, existingUser.email) } returns "access-token"
                    every { jwtProvider.generateRefreshToken(existingUser.uuid, existingUser.email) } returns "refresh-token"

                    val result = sut.registerOrLogin(request)

                    verify(exactly = 0) { userRepository.save(any()) }
                    result.uuid shouldBe existingUser.uuid
                    result.email shouldBe existingUser.email
                    result.accessToken shouldBe "access-token"
                    result.refreshToken shouldBe "refresh-token"
                }
            }
        }

        // ── getTokenForUser ──────────────────────────────────────────────────

        given("getTokenForUser 호출 시") {
            `when`("해당 UUID의 유저가 DB에 있으면") {
                then("해당 유저의 TokenResponse를 반환한다") {
                    val user = UserFixtures.user()

                    every { userRepository.findByUuid(user.uuid) } returns user
                    every { jwtProvider.generateAccessToken(user.uuid, user.email) } returns "access-token"
                    every { jwtProvider.generateRefreshToken(user.uuid, user.email) } returns "refresh-token"

                    val result = sut.getTokenForUser(user.uuid)

                    result.uuid shouldBe user.uuid
                    result.email shouldBe user.email
                    result.accessToken shouldBe "access-token"
                    result.refreshToken shouldBe "refresh-token"
                }
            }

            `when`("해당 UUID의 유저가 DB에 없으면") {
                then("UserNotFoundException을 던진다") {
                    val unknownUuid = UUID.randomUUID()
                    every { userRepository.findByUuid(unknownUuid) } returns null

                    shouldThrow<UserNotFoundException> {
                        sut.getTokenForUser(unknownUuid)
                    }
                }
            }
        }
    }
}
