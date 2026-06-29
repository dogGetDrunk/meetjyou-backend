package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.post.dto.UpdatePostStatusRequest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation
import jakarta.validation.Validator

class UpdatePostStatusTest : BehaviorSpec({
    val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    Given("UpdatePostStatusRequest 유효성 검사") {
        When("status가 RECRUITING인 경우") {
            Then("검증 통과") {
                val violations = validator.validate(UpdatePostStatusRequest(PostStatus.RECRUITING))
                violations.isEmpty() shouldBe true
            }
        }

        When("status가 RECRUITMENT_COMPLETED인 경우") {
            Then("검증 통과") {
                val violations = validator.validate(UpdatePostStatusRequest(PostStatus.RECRUITMENT_COMPLETED))
                violations.isEmpty() shouldBe true
            }
        }
    }
})
