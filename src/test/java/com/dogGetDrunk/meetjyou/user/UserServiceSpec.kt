package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.exception.business.user.InvalidEmailFormatException
import com.dogGetDrunk.meetjyou.common.exception.business.user.InvalidNicknameException
import com.dogGetDrunk.meetjyou.common.exception.business.user.TooLongBioException
import com.dogGetDrunk.meetjyou.common.exception.business.user.TooManyPersonalitiesException
import com.dogGetDrunk.meetjyou.common.exception.business.user.TooManyTravelStylesException
import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Diet
import com.dogGetDrunk.meetjyou.preference.Etc
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.TravelStyle
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import com.dogGetDrunk.meetjyou.user.support.UserTestBase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate

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
                    every { request.nickname } returns invalidNickname

                    Then("InvalidNicknameException 예외를 던진다: $invalidNickname") {
                        shouldThrow<InvalidNicknameException> {
                            userService.createUser(request)
                        }
                        verify(exactly = 0) { userRepository.save(any()) }
                    }
                }
            }

            When("길이 제한을 초과하는 한 줄 소개로 가입을 요청하면") {
                val tooLongBio = "a".repeat(31) + " with spaces"

                every { request.bio } returns tooLongBio
                every { request.nickname } returns "valid"

                Then("TooLongBioException 예외를 던진다.") {
                    shouldThrow<TooLongBioException> {
                        userService.createUser(request)
                    }
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }

            When("이메일 형식에 맞지 않는 이메일로 가입을 요청하면") {
                val invalidEmail = "invalid-email-format"

                every { request.email } returns invalidEmail
                every { request.nickname } returns "valid"
                every { request.bio } returns "Valid bio"

                Then("InvalidEmailFormatException 예외를 던진다.") {
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

                When("성격을 4개 이상 선택해 가입을 요청하면") {
                    val tooManyPersonalities = listOf(
                        Personality.OPTIMISTIC,
                        Personality.PRACTICAL,
                        Personality.EXTROVERTED,
                        Personality.SOCIAL,
                    )

                    every { request.personalities } returns tooManyPersonalities

                    Then("TooManyPersonalitiesException 예외를 던진다.") {
                        shouldThrow<TooManyPersonalitiesException> {
                            userService.createUser(request)
                        }
                        verify(exactly = 0) { userRepository.save(any()) }
                    }
                }

                When("여행 스타일을 4개 이상 선택해 가입을 요청하면") {
                    val tooManyTravelStyles = listOf(
                        TravelStyle.ACTIVITY,
                        TravelStyle.ADVENTURE,
                        TravelStyle.FOOD,
                        TravelStyle.SPORTS,
                    )

                    every { request.travelStyles } returns tooManyTravelStyles

                    Then("TooManyTravelStylesException 예외를 던진다.") {
                        shouldThrow<TooManyTravelStylesException> {
                            userService.createUser(request)
                        }
                        verify(exactly = 0) { userRepository.save(any()) }
                    }
                }
            }

            When("유효한 입력으로 가입을 요청하면") {
                Then("회원이 생성된다.") {
                    val validRequest = RegistrationRequest(
                        email = "valid@email.com",
                        nickname = "nickname",
                        bio = "This is a valid bio.",
                        birthDate = LocalDate.now(),
                        gender = Gender.M,
                        age = Age.TWENTY,
                        personalities = listOf(Personality.OPTIMISTIC, Personality.PRACTICAL),
                        travelStyles = listOf(TravelStyle.ACTIVITY, TravelStyle.ADVENTURE),
                        diet = listOf(Diet.VEGETARIAN, Diet.SPECIFIC),
                        etc = listOf(Etc.ANYTHING),
                        authProvider = AuthProvider.KAKAO,
                    )
                    every { userRepository.save(any()) } answers {
                        val arg = firstArg<User>()
                        User(
                            email = arg.email,
                            nickname = arg.nickname,
                            birthDate = arg.birthDate,
                            authProvider = arg.authProvider,
                        )
                    }
                    every { preferenceRepository.findByName(any()) } returns null

                    userService.createUser(validRequest).shouldBeInstanceOf<TokenResponse>()
                }
            }
        }
    }
}
