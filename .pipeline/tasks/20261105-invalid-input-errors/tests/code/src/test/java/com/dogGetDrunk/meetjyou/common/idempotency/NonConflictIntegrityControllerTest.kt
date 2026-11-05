package com.dogGetDrunk.meetjyou.common.idempotency

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PostNotFoundException
import com.dogGetDrunk.meetjyou.plan.PlanController
import com.dogGetDrunk.meetjyou.plan.PlanService
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanRequest
import com.dogGetDrunk.meetjyou.post.PostController
import com.dogGetDrunk.meetjyou.post.PostService
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

/**
 * AT-003-1: when the conflict recovery finds no key row (returns null), both controllers must
 * rethrow the original DataIntegrityViolationException instance, never a NotFound exception.
 */
class NonConflictIntegrityControllerTest : BehaviorSpec({

    val key = "non-conflict-key"
    val original = DataIntegrityViolationException("unmapped constraint violation")

    Given("AT-003-1 PostController with a service that throws DIVE and finds no key row") {
        val postService = mockk<PostService>()
        val controller = PostController(postService)
        val request = postRequest()
        every { postService.createPost(request, key) } throws original
        every { postService.resolveAfterConflict(request, key) } returns null

        When("createPost is called") {
            val thrown = shouldThrow<Throwable> { controller.createPost(request, key) }

            Then("the same DIVE instance is rethrown, not PostNotFoundException") {
                thrown shouldBeSameInstanceAs original
                (thrown is PostNotFoundException) shouldBe false
                thrown shouldNotBe beInstanceOf<PostNotFoundException>()
            }

            Then("recovery was invoked with the same request and key") {
                verify(exactly = 1) { postService.resolveAfterConflict(request, key) }
            }
        }
    }

    Given("AT-003-1 PlanController with a service that throws DIVE and finds no key row") {
        val planService = mockk<PlanService>()
        val controller = PlanController(planService)
        val request = planRequest()
        every { planService.createPlan(request, key) } throws original
        every { planService.resolveAfterConflict(request, key) } returns null

        When("createPlan is called") {
            val thrown = shouldThrow<Throwable> { controller.createPlan(request, key) }

            Then("the same DIVE instance is rethrown, not PlanNotFoundException") {
                thrown shouldBeSameInstanceAs original
                (thrown is PlanNotFoundException) shouldBe false
            }

            Then("recovery was invoked with the same request and key") {
                verify(exactly = 1) { planService.resolveAfterConflict(request, key) }
            }
        }
    }
})

private fun postRequest() = CreatePostRequest(
    title = "title",
    content = "content",
    isInstant = false,
    itinStart = Instant.parse("2030-05-01T00:00:00Z"),
    itinFinish = Instant.parse("2030-05-05T00:00:00Z"),
    location = "Seoul",
    capacity = 4,
    companionSpec = null,
    planUuid = null,
    isPlanPublic = null,
)

private fun planRequest() = CreatePlanRequest(
    title = "Seoul Trip",
    itinStart = Instant.parse("2030-05-01T00:00:00Z"),
    itinFinish = Instant.parse("2030-05-05T00:00:00Z"),
    location = "Seoul",
    centerLat = 37.5665,
    centerLng = 126.9780,
    memo = null,
    markers = emptyList(),
)
