package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.place.support.PlaceTestDoubles
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PostNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.post.PostUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyService
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.party.PartyRecruitmentStatus
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
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.UUID

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
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val idempotencyKeyService = mockk<IdempotencyKeyService>(relaxed = true)
    private val sut = PostService(
        postRepository, userRepository, compPreferenceRepository, preferenceRepository,
        partyService, planRepository, markerRepository, userPartyRepository, postViewService,
        currentUserProvider, chatRoomRepository, idempotencyKeyService,
            PlaceTestDoubles.resolver(), PlaceTestDoubles.regionCatalog(),
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
                every { postRepository.lockByUuid(post.uuid) } returns post
                every { currentUserProvider.uuid } returns author.uuid
            }

            `when`("상태를 바꾸면") {
                then("AT-003-1 게시글을 읽기 전에 연결된 파티 행을 잠근다") {
                    sut.updatePostStatus(post.uuid, request)

                    verifyOrder {
                        partyService.lockPartyOfPost(post.uuid)
                        postRepository.lockByUuid(post.uuid)
                    }
                }
            }

            `when`("게시글이 없으면") {
                then("잠금 단계의 PostNotFoundException이 그대로 전파되고 게시글을 읽지 않는다") {
                    val missing = UUID.randomUUID()
                    every { partyService.lockPartyOfPost(missing) } throws PostNotFoundException(missing)

                    shouldThrow<PostNotFoundException> {
                        sut.updatePostStatus(missing, request)
                    }
                    verify(exactly = 0) { postRepository.lockByUuid(missing) }
                }
            }

            `when`("파티가 아직 진행 중이면") {
                then("status가 정상적으로 변경된다") {
                    sut.updatePostStatus(post.uuid, request)

                    post.status shouldBe PostStatus.RECRUITMENT_COMPLETED
                }
            }

            `when`("모집 완료를 반복 요청하면") {
                then("AT-003-1 파티 모집 상태는 CLOSED로 유지된다") {
                    sut.updatePostStatus(post.uuid, request)
                    sut.updatePostStatus(post.uuid, request)

                    post.status shouldBe PostStatus.RECRUITMENT_COMPLETED
                    party.recruitmentStatus shouldBe PartyRecruitmentStatus.CLOSED
                    verify(exactly = 2) { partyService.lockPartyOfPost(post.uuid) }
                    verify(exactly = 2) { postRepository.lockByUuid(post.uuid) }
                }
            }

            `when`("완료된 모집을 두 번 재개하면") {
                then("AT-003-2 게시글과 파티 모집 상태가 열린 상태로 유지된다") {
                    sut.updatePostStatus(post.uuid, request)
                    val reopen = UpdatePostStatusRequest(status = PostStatus.RECRUITING)
                    sut.updatePostStatus(post.uuid, reopen)
                    sut.updatePostStatus(post.uuid, reopen)

                    post.status shouldBe PostStatus.RECRUITING
                    party.recruitmentStatus shouldBe PartyRecruitmentStatus.OPEN
                }
            }

            `when`("연결된 파티가 이미 COMPLETED 상태이면") {
                then("AT-003-3 InvalidInputException을 던지고 status는 변하지 않는다") {
                    party.complete()

                    shouldThrow<InvalidInputException> {
                        sut.updatePostStatus(post.uuid, request)
                    }
                    shouldThrow<InvalidInputException> {
                        sut.updatePostStatus(post.uuid, UpdatePostStatusRequest(PostStatus.RECRUITING))
                    }
                    post.status shouldBe PostStatus.RECRUITING
                    party.recruitmentStatus shouldBe PartyRecruitmentStatus.CLOSED
                }
            }

            `when`("작성자가 아닌 유저가 호출하면") {
                then("AT-003-3 PostUpdateAccessDeniedException을 던진다") {
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
