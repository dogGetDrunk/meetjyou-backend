package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.user.dto.UserUpdateRequest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation
import jakarta.validation.Validator

class UserRequestValidationTest : BehaviorSpec({
    val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    fun buildRequest(personalities: List<Personality>) = UserUpdateRequest(
        nickname = "테스트닉네임",
        bio = null,
        gender = Gender.M,
        age = Age.TWENTY,
        personalities = personalities,
        travelStyles = emptyList(),
        diet = emptyList(),
        etc = emptyList(),
    )

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
})
