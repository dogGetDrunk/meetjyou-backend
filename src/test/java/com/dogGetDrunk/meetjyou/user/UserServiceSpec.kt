package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.user.support.UserTestBase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class UserServiceSpec : UserTestBase() {
    private lateinit var userRepository: UserRepository
    private lateinit var preferenceRepository: PreferenceRepository
    private lateinit var userPreferenceRepository: UserPreferenceRepository
    private lateinit var jwtProvider: JwtProvider
    private lateinit var userService: UserService

    init {
        beforeTest {
            userRepository = mockk(relaxed = false)
            preferenceRepository = mockk(relaxed = false)
            userPreferenceRepository = mockk(relaxed = false)
            jwtProvider = mockk(relaxed = false)
            userService = UserService(
                userRepository,
                preferenceRepository,
                userPreferenceRepository,
                jwtProvider,
            )
        }

        Given("회원 가입") {
            When("중복된 닉네임으로 검사 요청이 오면") {
                Then("true를 반환한다") {
                    val nickname = UserFixtures.nickname()
                    every { userRepository.existsByNickname(nickname) } returns true

                    val result = runCatching { userService.isDuplicateNickname(nickname) }
                    result.isSuccess.shouldBeTrue()

                    verify(exactly = 1) { userRepository.existsByNickname(nickname) }
                    confirmVerified(userRepository)
                }
            }

            When("특수문자가 포함된 닉네임으로 가입 요청을 하면") {
                Then("InvalidNicknameException 예외를 던진다") {
                    val request = mockk<RegistrationRequest>(relaxed = true) {
                        every { nickname } returns "무효@닉네임"
                    }

                    val result = runCatching { userService.createUser(request) }
                    result.isSuccess.shouldBeFalse()
                    result.exceptionOrNull()!!.cause!!.javaClass.simpleName shouldBe "InvalidNicknameException"

                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }

            When("공백이 포함된 닉네임으로 가입 요청을 하면") {
                Then("InvalidNicknameException 예외를 던진다") {
                    val request = mockk<RegistrationRequest>(relaxed = true) {
                        every { nickname } returns "공백 닉네임"
                    }

                    val result = runCatching { userService.createUser(request) }
                    result.isSuccess.shouldBeFalse()
                    result.exceptionOrNull()!!.cause!!.javaClass.simpleName shouldBe "InvalidNicknameException"

                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }

            When("최대 길이 8자를 초과하는 닉네임으로 가입 요청을 하면") {
                Then("InvalidNicknameException 예외를 던진다") {
                    val request = mockk<RegistrationRequest>(relaxed = true) {
                        every { nickname } returns "닉네임이8자를초과합니다"
                    }

                    val result = runCatching { userService.createUser(request) }
                    result.isSuccess.shouldBeFalse()
                    result.exceptionOrNull()!!.cause!!.javaClass.simpleName shouldBe "InvalidNicknameException"

                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }
    }
}
