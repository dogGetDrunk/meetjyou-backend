package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.exception.business.user.InvalidEmailFormatException
import com.dogGetDrunk.meetjyou.common.exception.business.user.InvalidNicknameException
import com.dogGetDrunk.meetjyou.common.exception.business.user.TooLongBioException
import com.dogGetDrunk.meetjyou.common.exception.business.user.TooManyPersonalitiesException
import com.dogGetDrunk.meetjyou.common.exception.business.user.TooManyTravelStylesException
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.TravelStyle
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
            userRepository = mockk(relaxed = true)
            preferenceRepository = mockk(relaxed = true)
            userPreferenceRepository = mockk(relaxed = true)
            jwtProvider = mockk(relaxed = true)
            userService = UserService(
                userRepository,
                preferenceRepository,
                userPreferenceRepository,
                jwtProvider,
            )
        }

        Given("회원 가입") {
            val request = mockk<RegistrationRequest>(relaxed = true)
            every { userRepository.existsByEmail(any()) } returns false
            every { userRepository.existsByNickname(any()) } returns false

            When("유효하지 않은 닉네임으로 가입을 요청하면") {
                listOf(
                    "특수문자@닉네임",
                    "공백 닉네임",
                    "한",
                    "길이제한8자를초과하는닉네임",
                ).forEach { invalidNickname ->
                    Then("InvalidNicknameException 예외를 던진다: $invalidNickname") {
                        every { request.nickname } returns invalidNickname

                        shouldThrow<InvalidNicknameException> {
                            userService.createUser(request)
                        }

                        verify(exactly = 0) { userRepository.save(any()) }
                    }
                }
            }

            When("길이 제한을 초과하는 한 줄 소개로 가입을 요청하면") {
                Then("TooLongBioException 예외를 던진다.") {
                    val tooLongBio = "a".repeat(31) + " with spaces"

                    every { request.bio } returns tooLongBio
                    every { request.nickname } returns "valid"

                    shouldThrow<TooLongBioException> {
                        userService.createUser(request)
                    }

                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }

            When("이메일 형식에 맞지 않는 이메일로 가입을 요청하면") {
                Then("InvalidEmailFormatException 예외를 던진다.") {
                    val invalidEmail = "invalid-email-format"

                    every { request.email } returns invalidEmail
                    every { request.nickname } returns "valid"
                    every { request.bio } returns "Valid bio"

                    shouldThrow<InvalidEmailFormatException> {
                        userService.createUser(request)
                    }

                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }

            And("특성") {
                every { request.email } returns "valid@example.com"
                every { request.nickname } returns "valid"
                every { request.bio } returns "Valid bio"

                When("성격을 3개 이상 선택해 가입을 요청하면") {
                    Then("TooManyPersonalitiesException 예외를 던진다.") {
                        val tooManyPersonalities = listOf(
                            Personality.OPTIMISTIC,
                            Personality.PRACTICAL,
                            Personality.EXTROVERTED,
                            Personality.SOCIAL,
                        )

                        every { request.personalities } returns tooManyPersonalities

                        shouldThrow<TooManyPersonalitiesException> {
                            userService.createUser(request)
                        }

                        verify(exactly = 0) { userRepository.save(any()) }
                    }
                }

                When("여행 스타일을 3개 이상 선택해 가입을 요청하면") {
                    Then("TooManyTravelStylesException 예외를 던진다.") {
                        val tooManyTravelStyles = listOf(
                            TravelStyle.ACTIVITY,
                            TravelStyle.ADVENTURE,
                            TravelStyle.FOOD,
                            TravelStyle.SPORTS,
                        )

                        every { request.travelStyles } returns tooManyTravelStyles

                        shouldThrow<TooManyTravelStylesException> {
                            userService.createUser(request)
                        }

                        verify(exactly = 0) { userRepository.save(any()) }
                    }
                }
            }
        }
    }
}
