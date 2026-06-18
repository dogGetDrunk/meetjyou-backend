package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class GetPostApplicationStatusTest : BehaviorSpec() {

    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val compPreferenceRepository = mockk<CompPreferenceRepository>(relaxed = true)
    private val preferenceRepository = mockk<PreferenceRepository>(relaxed = true)
    private val partyService = mockk<PartyService>(relaxed = true)
    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val markerRepository = mockk<MarkerRepository>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val sut = PostService(
        postRepository, userRepository, compPreferenceRepository, preferenceRepository,
        partyService, planRepository, markerRepository, userPartyRepository,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        val user = UserFixtures.user()
        val party = NotificationCenterFixtures.party()
        val post = NotificationCenterFixtures.post(party, user)

        beforeEach {
            clearAllMocks()
            val principal = CustomUserPrincipal(user.uuid, user.email)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, null, emptyList())
            every { postRepository.findByUuid(post.uuid) } returns post
            every { compPreferenceRepository.findAllByPost(post) } returns emptyList()
        }

        afterEach { SecurityContextHolder.clearContext() }

        given("getPostByUuid 호출 시") {
            `when`("신청 이력이 없는 경우") {
                then("myApplicationStatus가 null이다") {
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, user.uuid) } returns null

                    sut.getPostByUuid(post.uuid).myApplicationStatus shouldBe null
                }
            }

            `when`("신청 중(PENDING)인 경우") {
                then("myApplicationStatus가 PENDING이다") {
                    val userParty = NotificationCenterFixtures.pendingUserParty(party, user)
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, user.uuid) } returns userParty

                    sut.getPostByUuid(post.uuid).myApplicationStatus shouldBe MemberStatus.PENDING
                }
            }

            `when`("가입된(JOINED) 경우") {
                then("myApplicationStatus가 JOINED이다") {
                    val userParty = NotificationCenterFixtures.hostUserParty(party, user)
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, user.uuid) } returns userParty

                    sut.getPostByUuid(post.uuid).myApplicationStatus shouldBe MemberStatus.JOINED
                }
            }

            `when`("신청이 거절(REJECTED)된 경우") {
                then("myApplicationStatus가 REJECTED이다") {
                    val userParty = NotificationCenterFixtures.pendingUserParty(party, user).also { it.reject() }
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, user.uuid) } returns userParty

                    sut.getPostByUuid(post.uuid).myApplicationStatus shouldBe MemberStatus.REJECTED
                }
            }
        }
    }
}
