package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation
import jakarta.validation.Validator
import java.time.Instant
import java.time.temporal.ChronoUnit

class PostRequestValidationTest : BehaviorSpec({
    val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    fun buildRequest(itinStart: Instant) = CreatePostRequest(
        title = "테스트 여행",
        content = "같이 가실 분",
        isInstant = false,
        itinStart = itinStart,
        itinFinish = itinStart.plus(1, ChronoUnit.DAYS),
        location = "제주도",
        capacity = 4,
        companionSpec = null,
        planUuid = null,
        isPlanPublic = null,
    )

    fun hasItinStartError(request: CreatePostRequest): Boolean =
        validator.validate(request).any { it.propertyPath.toString() == "itinStartAfterNow" }

    val nowTruncated = Instant.now().truncatedTo(ChronoUnit.MINUTES)

    Given("itinStart가 허용 범위(분 기준 -2분) 이내일 때") {
        When("itinStart가 현재 시각인 경우") {
            Then("검증 통과") {
                hasItinStartError(buildRequest(Instant.now())) shouldBe false
            }
        }

        When("itinStart가 분 기준으로 정확히 2분 전인 경우 (경계값)") {
            Then("검증 통과") {
                hasItinStartError(buildRequest(nowTruncated.minus(2, ChronoUnit.MINUTES))) shouldBe false
            }
        }

        When("itinStart가 2분 45초 전이지만 분 기준으로는 2분 전인 경우") {
            Then("초 단위는 무시되어 검증 통과") {
                val itinStart = nowTruncated.minus(2, ChronoUnit.MINUTES).plusSeconds(45)
                hasItinStartError(buildRequest(itinStart)) shouldBe false
            }
        }

        When("itinStart가 내일인 경우") {
            Then("검증 통과") {
                hasItinStartError(buildRequest(Instant.now().plus(1, ChronoUnit.DAYS))) shouldBe false
            }
        }
    }

    Given("itinStart가 허용 범위를 초과한 과거일 때") {
        When("itinStart가 분 기준으로 3분 전인 경우") {
            Then("검증 실패") {
                hasItinStartError(buildRequest(nowTruncated.minus(3, ChronoUnit.MINUTES))) shouldBe true
            }
        }

        When("itinStart가 1시간 전인 경우") {
            Then("검증 실패") {
                hasItinStartError(buildRequest(Instant.now().minus(1, ChronoUnit.HOURS))) shouldBe true
            }
        }
    }
})
