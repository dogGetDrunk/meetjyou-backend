package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.support.PlanFixtures
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.dogGetDrunk.meetjyou.post.view.PostViewService
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Regression: a post must not be able to reference a plan the author does not own.
 * Without the ownership guard in resolvePlanReference, attaching someone else's private
 * plan with isPlanPublic=true would leak it (embedded in the post response, and made
 * world-readable via PlanAccessGuard#existsByPlan_UuidAndIsPlanPublicTrue).
 */
class PostPlanOwnershipTest : BehaviorSpec() {

    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val compPreferenceRepository = mockk<CompPreferenceRepository>(relaxed = true)
    private val preferenceRepository = mockk<PreferenceRepository>(relaxed = true)
    private val partyService = mockk<PartyService>(relaxed = true)
    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val markerRepository = mockk<MarkerRepository>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val postViewService = mockk<PostViewService>(relaxed = true)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val sut = PostService(
        postRepository, userRepository, compPreferenceRepository, preferenceRepository,
        partyService, planRepository, markerRepository, userPartyRepository, postViewService,
        currentUserProvider,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun updateRequest(post: Post, planUuid: java.util.UUID?, isPlanPublic: Boolean?) = UpdatePostRequest(
        title = post.title,
        content = post.content,
        isInstant = post.isInstant,
        itinStart = post.itinStart,
        itinFinish = post.itinFinish,
        location = post.location,
        capacity = post.capacity,
        companionSpec = null,
        planUuid = planUuid,
        isPlanPublic = isPlanPublic,
    )

    init {
        val user = UserFixtures.user()
        val party = NotificationCenterFixtures.party()
        val post = NotificationCenterFixtures.post(party, user)

        beforeEach {
            clearAllMocks()
            val principal = CustomUserPrincipal(user.uuid, user.email)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, null, emptyList())
            every { currentUserProvider.uuid } returns user.uuid
            every { postRepository.findByUuid(post.uuid) } returns post
        }

        afterEach { SecurityContextHolder.clearContext() }

        given("updatePost 호출 시 다른 유저가 소유한 planUuid가 전달되면") {
            `when`("소유권 없는 플랜을 모집글에 연결하려 하면") {
                then("PlanUpdateAccessDeniedException(403)이 발생하고 plan은 바뀌지 않는다") {
                    val otherUser = UserFixtures.user(email = "other@example.com", nickname = "other")
                    val otherPlan = PlanFixtures.plan(owner = otherUser)
                    every { planRepository.findByUuid(otherPlan.uuid) } returns otherPlan

                    shouldThrow<PlanUpdateAccessDeniedException> {
                        sut.updatePost(post.uuid, updateRequest(post, otherPlan.uuid, true))
                    }

                    post.plan shouldBe null
                }
            }
        }

        given("updatePost 호출 시 본인이 소유한 planUuid가 전달되면") {
            `when`("소유한 플랜을 모집글에 연결하면") {
                then("정상적으로 연결된다") {
                    val ownPlan = PlanFixtures.plan(owner = user)
                    every { planRepository.findByUuid(ownPlan.uuid) } returns ownPlan

                    sut.updatePost(post.uuid, updateRequest(post, ownPlan.uuid, true))

                    post.plan shouldBe ownPlan
                    post.isPlanPublic shouldBe true
                }
            }
        }
    }
}
