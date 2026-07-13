package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.ChatRoomNotFoundException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

// Regression test: parties created before e7f7f78 have no matching chat_room row.
// getMyParties must surface that as a handled ChatRoomNotFoundException (404), not an
// unhandled IllegalStateException (500).
class GetMyPartiesTest : BehaviorSpec() {

    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val markerRepository = mockk<MarkerRepository>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val chatParticipantService = mockk<ChatParticipantService>(relaxed = true)
    private val chatRoomEventBroadcaster = mockk<ChatRoomEventBroadcaster>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val partyImgService = mockk<PartyImgService>(relaxed = true)
    private val postImgService = mockk<PostImgService>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)

    private val sut = PartyService(
        partyRepository, postRepository, planRepository, markerRepository, chatRoomRepository,
        chatParticipantService, chatRoomEventBroadcaster, userPartyRepository, userRepository,
        publisher, partyImgService, postImgService, objectMapper, currentUserProvider,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("getMyParties 호출 시") {
            val user = UserFixtures.user()
            val party = NotificationCenterFixtures.party()
            val userParty = NotificationCenterFixtures.hostUserParty(party, user)
            val pageable = PageRequest.of(0, 10)

            beforeEach {
                every { currentUserProvider.uuid } returns user.uuid
                every {
                    userPartyRepository.findAllWithPartyByUserUuidAndMemberStatus(user.uuid, MemberStatus.JOINED, pageable)
                } returns PageImpl(listOf(userParty))
            }

            `when`("파티에 매칭되는 ChatRoom이 존재하면") {
                then("roomUuid를 포함한 응답을 반환한다") {
                    val chatRoom = ChatRoom(party = party)
                    every {
                        chatRoomRepository.findAllWithPartyByPartyUuidIn(listOf(party.uuid))
                    } returns listOf(chatRoom)

                    val result = sut.getMyParties(pageable)

                    result.content.single().roomUuid shouldBe chatRoom.uuid
                }
            }

            `when`("파티에 매칭되는 ChatRoom이 없으면 (레거시 데이터)") {
                then("500 대신 ChatRoomNotFoundException을 던진다") {
                    every {
                        chatRoomRepository.findAllWithPartyByPartyUuidIn(listOf(party.uuid))
                    } returns emptyList()

                    shouldThrow<ChatRoomNotFoundException> {
                        sut.getMyParties(pageable)
                    }
                }
            }
        }
    }
}
