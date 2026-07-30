package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.post.PostUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostStatusRequest
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

class UpdatePostStatusServiceTest : BehaviorSpec() {

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
        beforeEach { clearAllMocks() }

        given("PostService.updatePostStatus 호출 시") {
            val author = UserFixtures.user()
            val party = NotificationCenterFixtures.party()
            val post = NotificationCenterFixtures.post(party, author)
            val request = UpdatePostStatusRequest(status = PostStatus.RECRUITMENT_COMPLETED)

            beforeEach {
                every { postRepository.findByUuid(post.uuid) } returns post
                every { currentUserProvider.uuid } returns author.uuid
            }

            `when`("파티가 아직 진행 중이면") {
                then("status가 정상적으로 변경된다") {
                    sut.updatePostStatus(post.uuid, request)

                    post.status shouldBe PostStatus.RECRUITMENT_COMPLETED
                }
            }

            `when`("연결된 파티가 이미 COMPLETED 상태이면") {
                then("InvalidInputException을 던지고 status는 변하지 않는다") {
                    party.complete()

                    shouldThrow<InvalidInputException> {
                        sut.updatePostStatus(post.uuid, request)
                    }
                    post.status shouldBe PostStatus.RECRUITING
                }
            }

            `when`("작성자가 아닌 유저가 호출하면") {
                then("PostUpdateAccessDeniedException을 던진다") {
                    val stranger = UserFixtures.user(email = "stranger@test.com", nickname = "stranger", externalId = "ext-stranger")
                    every { currentUserProvider.uuid } returns stranger.uuid

                    shouldThrow<PostUpdateAccessDeniedException> {
                        sut.updatePostStatus(post.uuid, request)
                    }
                }
            }
        }
    }
}
