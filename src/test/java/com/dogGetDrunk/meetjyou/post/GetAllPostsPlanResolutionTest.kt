package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.support.PlanFixtures
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
import io.mockk.verify
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class GetAllPostsPlanResolutionTest : BehaviorSpec() {

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

    init {
        val user = UserFixtures.user()
        val party = NotificationCenterFixtures.party()
        val plan = PlanFixtures.plan(user)
        val post = NotificationCenterFixtures.post(party, user).also {
            it.plan = plan
            it.isPlanPublic = true
        }

        beforeEach {
            clearAllMocks()
            val principal = CustomUserPrincipal(user.uuid, user.email)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, null, emptyList())
            every { currentUserProvider.uuid } returns user.uuid
            every { postRepository.findAllWithAuthor(any<Pageable>()) } returns PageImpl(listOf(post))
            every { compPreferenceRepository.findAllByPostIn(listOf(post)) } returns emptyList()
            every { markerRepository.findAllByPlan_UuidIn(listOf(plan.uuid)) } returns emptyList()
            every {
                userPartyRepository.findAllByParty_UuidInAndUser_Uuid(listOf(party.uuid), user.uuid)
            } returns emptyList()
        }

        afterEach { SecurityContextHolder.clearContext() }

        given("공개된 plan이 연결된 게시글 목록 조회 시") {
            `when`("getAllPosts를 호출하면") {
                then("planRepository를 다시 조회하지 않고 post.plan을 그대로 사용해 응답에 포함한다") {
                    val result = sut.getAllPosts(PageRequest.of(0, 10))

                    result.content.single().plan?.uuid shouldBe plan.uuid
                    verify(exactly = 0) { planRepository.findByUuid(any()) }
                }
            }
        }
    }
}
