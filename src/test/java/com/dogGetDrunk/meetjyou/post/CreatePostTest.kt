package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
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
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID

class CreatePostTest : BehaviorSpec() {

    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val compPreferenceRepository = mockk<CompPreferenceRepository>(relaxed = true)
    private val preferenceRepository = mockk<PreferenceRepository>(relaxed = true)
    private val partyService = mockk<PartyService>(relaxed = true)
    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val markerRepository = mockk<MarkerRepository>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val postViewService = mockk<PostViewService>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val sut = PostService(
        postRepository, userRepository, compPreferenceRepository, preferenceRepository,
        partyService, planRepository, markerRepository, userPartyRepository, postViewService,
        chatRoomRepository, currentUserProvider,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("createPost 호출 시") {
            val author = UserFixtures.user()
            val request = CreatePostRequest(
                title = "Seoul Trip",
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

            beforeEach {
                every { userRepository.findByUuid(author.uuid) } returns author
                every { currentUserProvider.uuid } returns author.uuid
            }

            `when`("clientRequestId 없이 요청하면") {
                then("항상 새로운 파티/게시글을 생성한다") {
                    val party = NotificationCenterFixtures.party()
                    val chatRoom = ChatRoom(party = party)
                    every { partyService.createParty(any()) } returns PartyService.PartyCreationResult(party, chatRoom)
                    every { postRepository.save(any()) } answers { firstArg() }

                    sut.createPost(request)

                    verify(exactly = 1) { partyService.createParty(any()) }
                    verify(exactly = 1) { postRepository.save(any()) }
                }
            }

            `when`("동일한 clientRequestId로 이미 생성된 게시글이 있으면(응답 유실 재시도)") {
                then("새로 생성하지 않고 기존 게시글을 반환한다") {
                    val clientRequestId = UUID.randomUUID()
                    val retryRequest = request.copy(clientRequestId = clientRequestId)
                    val party = NotificationCenterFixtures.party()
                    val existingPost = NotificationCenterFixtures.post(party, author)
                    val chatRoom = ChatRoom(party = party)

                    every {
                        postRepository.findByAuthor_UuidAndClientRequestId(author.uuid, clientRequestId)
                    } returns existingPost
                    every { chatRoomRepository.findByParty_Uuid(party.uuid) } returns chatRoom

                    val result = sut.createPost(retryRequest)

                    result.uuid shouldBe existingPost.uuid
                    verify(exactly = 0) { partyService.createParty(any()) }
                    verify(exactly = 0) { postRepository.save(any()) }
                }
            }
        }
    }
}
