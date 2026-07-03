package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
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
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class UpdatePostPlanSyncTest : BehaviorSpec() {

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

        given("updatePost 호출 시 planUuid가 함께 전달되면") {
            `when`("새 여행 계획서로 교체하면") {
                then("모집글과 연결된 파티의 plan이 함께 갱신된다") {
                    val newPlan = PlanFixtures.plan(owner = user)
                    every { planRepository.findByUuid(newPlan.uuid) } returns newPlan

                    sut.updatePost(post.uuid, updateRequest(post, newPlan.uuid, true))

                    post.plan shouldBe newPlan
                    post.isPlanPublic shouldBe true
                    party.plan shouldBe newPlan
                }
            }

            `when`("planUuid가 null이면") {
                then("모집글과 연결된 파티의 plan이 함께 해제된다") {
                    val existingPlan = PlanFixtures.plan(owner = user)
                    post.plan = existingPlan
                    post.isPlanPublic = true
                    party.plan = existingPlan

                    sut.updatePost(post.uuid, updateRequest(post, null, null))

                    post.plan shouldBe null
                    post.isPlanPublic shouldBe null
                    party.plan shouldBe null
                }
            }
        }
    }
}
