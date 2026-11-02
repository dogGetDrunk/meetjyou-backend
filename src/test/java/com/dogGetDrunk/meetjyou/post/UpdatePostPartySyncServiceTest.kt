package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyService
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyCapacityBelowJoinedException
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
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
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Instant
import java.util.UUID

class UpdatePostPartySyncServiceTest : BehaviorSpec() {

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
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun updateRequest(capacity: Int, planUuid: UUID? = null) = UpdatePostRequest(
        title = "New title", content = "New content", isInstant = false,
        itinStart = NEW_START, itinFinish = NEW_FINISH, location = NEW_LOCATION,
        capacity = capacity, companionSpec = null, planUuid = planUuid, isPlanPublic = null,
    )

    init {
        val user = UserFixtures.user()
        val party = Party(
            itinStart = OLD_START, itinFinish = OLD_FINISH, destination = OLD_LOCATION,
            joined = JOINED, capacity = INITIAL_CAPACITY, name = "Party",
        )
        val post = Post(
            party = party, isInstant = false, title = "Old title", content = "Old content",
            itinStart = OLD_START, itinFinish = OLD_FINISH, location = OLD_LOCATION, capacity = INITIAL_CAPACITY,
        )
        post.author = user

        beforeEach {
            clearAllMocks()
            every { currentUserProvider.uuid } returns user.uuid
            every { postRepository.lockByUuid(post.uuid) } returns post
        }

        given("모집글과 파티의 정원이 같은 상태에서") {
            `when`("정원과 일정 및 목적지를 수정하면") {
                then("AT-001-1 잠금 후 네 필드를 파티에 동기화한다") {
                    sut.updatePost(post.uuid, updateRequest(INCREASED_CAPACITY))
                    verifyOrder {
                        partyService.lockPartyOfPost(post.uuid)
                        postRepository.lockByUuid(post.uuid)
                    }
                    verify(exactly = 0) { postRepository.findByUuid(post.uuid) }
                    post.capacity shouldBe INCREASED_CAPACITY
                    party.capacity shouldBe INCREASED_CAPACITY
                    post.itinStart shouldBe NEW_START
                    party.itinStart shouldBe NEW_START
                    post.itinFinish shouldBe NEW_FINISH
                    party.itinFinish shouldBe NEW_FINISH
                    post.location shouldBe NEW_LOCATION
                    party.destination shouldBe NEW_LOCATION
                    party.joined shouldBe JOINED
                }
            }

            `when`("정원을 현재 참여 인원과 같게 줄이면") {
                then("AT-002-1 경계값을 허용한다") {
                    sut.updatePost(post.uuid, updateRequest(JOINED))
                    post.capacity shouldBe JOINED
                    party.capacity shouldBe JOINED
                    party.joined shouldBe JOINED
                }
            }

            `when`("정원을 현재 참여 인원보다 작게 줄이면") {
                then("AT-002-2 AT-002-3 계획서 조회 및 선호도 변경 전에 거부한다") {
                    shouldThrow<PartyCapacityBelowJoinedException> {
                        sut.updatePost(post.uuid, updateRequest(BELOW_JOINED, MISSING_PLAN))
                    }
                    verifyOrder {
                        partyService.lockPartyOfPost(post.uuid)
                        postRepository.lockByUuid(post.uuid)
                    }
                    verify(exactly = 0) { planRepository.findByUuid(any()) }
                    post.capacity shouldBe INITIAL_CAPACITY
                    party.capacity shouldBe INITIAL_CAPACITY
                    post.title shouldBe "Old title"
                    post.location shouldBe OLD_LOCATION
                    party.destination shouldBe OLD_LOCATION
                }
            }
        }
    }

    companion object {
        private const val JOINED = 3
        private const val INITIAL_CAPACITY = 5
        private const val INCREASED_CAPACITY = 10
        private const val BELOW_JOINED = 2
        private const val OLD_LOCATION = "Busan"
        private const val NEW_LOCATION = "Jeju"
        private val OLD_START = Instant.parse("2030-05-01T00:00:00Z")
        private val OLD_FINISH = Instant.parse("2030-05-03T00:00:00Z")
        private val NEW_START = Instant.parse("2030-06-01T00:00:00Z")
        private val NEW_FINISH = Instant.parse("2030-06-05T00:00:00Z")
        private val MISSING_PLAN = UUID.fromString("00000000-0000-0000-0000-000000000099")
    }
}
