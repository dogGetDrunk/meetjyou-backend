package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.exception.business.user.InvalidNicknameException
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.support.UserTestBase
import io.kotest.assertions.throwables.shouldThrow
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
            When("유효하지 않은 닉네임으로 가입을 요청하면") {
                listOf(
                    "특수문자@닉네임",
                    "공백 닉네임",
                    "길이제한8자를초과하는닉네임",
                ).forEach { invalidNickname ->
                    Then("InvalidNicknameException 예외를 던진다: $invalidNickname") {
                        val request = mockk<RegistrationRequest>(relaxed = true) {
                            every { nickname } returns invalidNickname
                        }

                        every { userRepository.existsByEmail(any()) } returns false
                        every { userRepository.existsByNickname(any()) } returns false

                        shouldThrow<InvalidNicknameException> {
                            userService.createUser(request)
                        }

                        verify(exactly = 0) { userRepository.save(any()) }
                    }
                }
            }
        }
    }
}
