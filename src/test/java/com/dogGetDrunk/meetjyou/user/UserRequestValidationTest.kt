package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.post.dto.PostAuthorSummary
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.UserUpdateRequest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation
import jakarta.validation.Validator

class UserRequestValidationTest : BehaviorSpec({
    val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    fun buildRequest(personalities: List<Personality>, bio: String? = null) = UserUpdateRequest(
        nickname = "테스트닉네임",
        bio = bio,
        gender = Gender.M,
        age = Age.TWENTY,
        personalities = personalities,
        travelStyles = emptyList(),
        diet = emptyList(),
        etc = emptyList(),
    )

    fun buildRegistration(bio: String?) = RegistrationRequest(
        nickname = "테스트닉네임",
        bio = bio,
        gender = Gender.M,
        age = Age.TWENTY,
        personalities = listOf(Personality.INTROVERTED),
        travelStyles = emptyList(),
        diet = emptyList(),
        etc = emptyList(),
        authProvider = AuthProvider.GOOGLE,
        idToken = "id-token",
        agreedTermsUuids = emptyList(),
    )

    fun hasBioError(request: Any): Boolean =
        validator.validate(request).any { it.propertyPath.toString() == "bio" }

    fun hasPersonalitiesError(request: UserUpdateRequest): Boolean =
        validator.validate(request).any { it.propertyPath.toString() == "personalities" }

    Given("personalities가 비어있을 때") {
        When("업데이트 요청을 검증하면") {
            Then("검증 실패") {
                hasPersonalitiesError(buildRequest(emptyList())) shouldBe true
            }
        }
    }

    Given("personalities가 1~3개일 때") {
        When("업데이트 요청을 검증하면") {
            Then("검증 통과") {
                hasPersonalitiesError(buildRequest(listOf(Personality.INTROVERTED))) shouldBe false
                hasPersonalitiesError(
                    buildRequest(listOf(Personality.INTROVERTED, Personality.SOCIAL, Personality.BOLD))
                ) shouldBe false
            }
        }
    }

    Given("personalities가 4개 이상일 때") {
        When("업데이트 요청을 검증하면") {
            Then("검증 실패") {
                hasPersonalitiesError(
                    buildRequest(
                        listOf(Personality.INTROVERTED, Personality.SOCIAL, Personality.BOLD, Personality.FREE)
                    )
                ) shouldBe true
            }
        }
    }

    Given("한 줄 소개에 한글 자모가 포함될 때") {
        val bios = listOf("ㅇㅇ", "ㅋㅋ 반가워요", "ㅏㅑ ㅗㅛ", "hello 123 안녕")
        When("가입·수정 요청을 검증하면") {
            Then("검증 통과") {
                bios.forEach { bio ->
                    hasBioError(buildRegistration(bio)) shouldBe false
                    hasBioError(buildRequest(listOf(Personality.INTROVERTED), bio)) shouldBe false
                }
            }
        }
    }

    Given("한 줄 소개에 특수문자나 이모지가 포함될 때") {
        val bios = listOf("안녕!", "여행 좋아요.", "hi😀", "a,b")
        When("가입·수정 요청을 검증하면") {
            Then("검증 실패") {
                bios.forEach { bio ->
                    hasBioError(buildRegistration(bio)) shouldBe true
                    hasBioError(buildRequest(listOf(Personality.INTROVERTED), bio)) shouldBe true
                }
            }
        }
    }

    Given("닉네임에 한글 자모가 포함될 때") {
        When("가입 요청을 검증하면") {
            Then("기존 규칙대로 검증 실패") {
                val request = buildRegistration(null).copy(nickname = "ㅋㅋ닉네임")
                validator.validate(request).any { it.propertyPath.toString() == "nickname" } shouldBe true
            }
        }
    }

    // Post responses show this placeholder for withdrawn authors; a real user holding it would be
    // indistinguishable from a withdrawn one.
    Given("닉네임이 탈퇴 작성자 표시 문구와 같을 때") {
        val nickname = PostAuthorSummary.WITHDRAWN_NICKNAME
        When("가입·수정 요청을 검증하면") {
            Then("둘 다 검증 실패") {
                val requests = listOf(
                    buildRegistration(null).copy(nickname = nickname),
                    buildRequest(listOf(Personality.INTROVERTED)).copy(nickname = nickname),
                )
                for (request in requests) {
                    validator.validate(request).any { it.propertyPath.toString() == "nickname" } shouldBe true
                }
            }
        }
    }
})
