package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.idempotency.IdempotencyKeyConflictException
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyService
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyScope
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
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
import java.time.Instant
import java.util.UUID

class CreatePostIdempotencyTest : BehaviorSpec() {

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

    private fun request() = CreatePostRequest(
        title = "Trip",
        content = "content",
        isInstant = false,
        itinStart = Instant.now().plusSeconds(3600),
        itinFinish = Instant.now().plusSeconds(7200),
        location = "Seoul",
        capacity = 4,
        companionSpec = null,
        planUuid = null,
        isPlanPublic = null,
    )

    init {
        beforeEach { clearAllMocks() }

        val user = UserFixtures.user()
        val party = NotificationCenterFixtures.party()
        val chatRoom = ChatRoom(party = party)
        val partyResult = PartyService.PartyCreationResult(party, chatRoom)

        beforeEach {
            every { currentUserProvider.uuid } returns user.uuid
            every { userRepository.findByUuid(user.uuid) } returns user
        }

        given("createPost 호출 시 idempotencyKey가 없으면") {
            `when`("호출하면") {
                then("기존과 동일하게 생성하고 idempotencyKeyService와는 상호작용하지 않는다") {
                    every { partyService.createParty(any()) } returns partyResult
                    every { postRepository.save(any<Post>()) } returnsArgument 0

                    sut.createPost(request(), null)

                    verify(exactly = 1) { partyService.createParty(any()) }
                    verify(exactly = 0) { idempotencyKeyService.resolveExisting(any(), any(), any(), any()) }
                    verify(exactly = 0) { idempotencyKeyService.record(any(), any(), any(), any(), any()) }
                }
            }
        }

        given("createPost 호출 시 idempotencyKey가 있고 기존 매치가 없으면") {
            `when`("호출하면") {
                then("정상 생성 후 record()가 1회 호출된다") {
                    every { idempotencyKeyService.hashRequest(any()) } returns "hash-1"
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_POST, user, "key-1", "hash-1")
                    } returns null
                    every { partyService.createParty(any()) } returns partyResult
                    every { postRepository.save(any<Post>()) } returnsArgument 0

                    sut.createPost(request(), "key-1")

                    verify(exactly = 1) { partyService.createParty(any()) }
                    verify(exactly = 1) {
                        idempotencyKeyService.record(IdempotencyScope.CREATE_POST, user, "key-1", any(), "hash-1")
                    }
                }
            }
        }

        given("createPost 호출 시 idempotencyKey가 있고 기존 매치(같은 바디)가 있으면") {
            `when`("호출하면") {
                then("생성 로직을 건너뛰고 기존 리소스로 응답을 재구성한다") {
                    val existingPostUuid = UUID.randomUUID()
                    val existingPost = NotificationCenterFixtures.post(party, user)

                    every { idempotencyKeyService.hashRequest(any()) } returns "hash-2"
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_POST, user, "key-2", "hash-2")
                    } returns existingPostUuid
                    every { postRepository.findByUuid(existingPostUuid) } returns existingPost
                    every { compPreferenceRepository.findAllByPost(existingPost) } returns emptyList()
                    every { chatRoomRepository.findByParty_Uuid(party.uuid) } returns chatRoom

                    val result = sut.createPost(request(), "key-2")

                    result.uuid shouldBe existingPost.uuid
                    verify(exactly = 0) { partyService.createParty(any()) }
                    verify(exactly = 0) { postRepository.save(any<Post>()) }
                    verify(exactly = 0) { idempotencyKeyService.record(any(), any(), any(), any(), any()) }
                }
            }
        }

        given("createPost 호출 시 idempotencyKey가 있고 기존 매치(다른 바디)가 있으면") {
            `when`("호출하면") {
                then("IdempotencyKeyConflictException을 던지고 생성 로직은 실행되지 않는다") {
                    every { idempotencyKeyService.hashRequest(any()) } returns "hash-3"
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_POST, user, "key-3", "hash-3")
                    } throws IdempotencyKeyConflictException("key-3")

                    shouldThrow<IdempotencyKeyConflictException> {
                        sut.createPost(request(), "key-3")
                    }

                    verify(exactly = 0) { partyService.createParty(any()) }
                }
            }
        }
    }
}
