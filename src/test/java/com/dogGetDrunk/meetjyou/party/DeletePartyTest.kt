package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.context.ApplicationEventPublisher

class DeletePartyTest : BehaviorSpec() {

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
        chatParticipantService, chatRoomEventBroadcaster, userPartyRepository,
        userRepository, publisher, partyImgService, postImgService, objectMapper, currentUserProvider,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("deleteParty 호출 시") {
            val host = UserFixtures.user()
            val party = NotificationCenterFixtures.party()
            val hostMembership = NotificationCenterFixtures.hostUserParty(party, host)

            beforeEach {
                every { currentUserProvider.uuid } returns host.uuid
                every { partyRepository.findByUuid(party.uuid) } returns party
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership
            }

            `when`("채팅방과 모집글이 연결된 파티를 삭제하면") {
                then("FK 의존 행(채팅 데이터, 채팅방, 모집글, 멤버십)을 모두 정리한 뒤 파티를 삭제한다") {
                    val chatRoom = ChatRoom(party = party)
                    val post = NotificationCenterFixtures.post(party, host)
                    every { chatRoomRepository.findByParty_Uuid(party.uuid) } returns chatRoom
                    every { postRepository.findByParty_Uuid(party.uuid) } returns post

                    sut.deleteParty(party.uuid)

                    verifyOrder {
                        chatParticipantService.purgeRoomData(chatRoom.uuid)
                        chatRoomRepository.delete(chatRoom)
                        postRepository.delete(post)
                        userPartyRepository.deleteAllByParty_Uuid(party.uuid)
                        partyRepository.delete(party)
                    }
                }
            }

            `when`("채팅방과 모집글이 없는 레거시 파티를 삭제하면") {
                then("멤버십만 정리하고 파티를 삭제한다") {
                    every { chatRoomRepository.findByParty_Uuid(party.uuid) } returns null
                    every { postRepository.findByParty_Uuid(party.uuid) } returns null

                    sut.deleteParty(party.uuid)

                    verify(exactly = 0) { chatParticipantService.purgeRoomData(any()) }
                    verify(exactly = 0) { postRepository.delete(any()) }
                    verify(exactly = 1) { userPartyRepository.deleteAllByParty_Uuid(party.uuid) }
                    verify(exactly = 1) { partyRepository.delete(party) }
                }
            }

            `when`("종료된 파티를 삭제하려 하면") {
                then("PartyUpdateAccessDeniedException을 던지고 아무것도 삭제하지 않는다") {
                    party.complete()

                    shouldThrow<PartyUpdateAccessDeniedException> {
                        sut.deleteParty(party.uuid)
                    }
                    verify(exactly = 0) { partyRepository.delete(any()) }
                }
            }
        }
    }
}
