package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.connection.ChatSessionTracker
import com.dogGetDrunk.meetjyou.chat.message.ChatMessage
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.chat.ChatRoomAccessDeniedException
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate

class ChatServiceTest : BehaviorSpec() {

    private val chatMessageRepository = mockk<ChatMessageRepository>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val chatSessionTracker = mockk<ChatSessionTracker>(relaxed = true)
    private val chatReadService = mockk<ChatReadService>(relaxed = true)
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val sut = ChatService(
        chatMessageRepository, chatRoomRepository, messagingTemplate,
        userRepository, partyRepository, userPartyRepository,
        chatSessionTracker, chatReadService, publisher,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("handleChatMessage 호출 시") {
            val sender = UserFixtures.user()
            val senderUuid = sender.uuid
            val party = NotificationCenterFixtures.party()
            val partyUuid = party.uuid
            val room = ChatRoom(party = party)
            val roomUuid = room.uuid
            val request = ChatMessageRequest(roomUuid = roomUuid, message = "hello")

            beforeEach {
                every { chatRoomRepository.findByUuid(roomUuid) } returns room
                every { chatRoomRepository.findPartyUuidByRoomUuid(roomUuid) } returns partyUuid
                every { userRepository.findByUuid(senderUuid) } returns sender
                every { partyRepository.findByUuid(partyUuid) } returns party
                every { chatSessionTracker.getConnectedUsers(roomUuid) } returns emptySet()
                every { userPartyRepository.findAllWithUserByPartyUuid(partyUuid) } returns emptyList()
            }

            `when`("JOINED 상태 멤버가 메시지를 전송하면") {
                then("메시지가 저장된다") {
                    val membership = NotificationCenterFixtures.hostUserParty(party, sender)
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, senderUuid) } returns membership
                    every { chatMessageRepository.save(any()) } returns mockk(relaxed = true)

                    sut.handleChatMessage(request, senderUuid)

                    verify(exactly = 1) { chatMessageRepository.save(any<ChatMessage>()) }
                }
            }

            `when`("파티에 가입되지 않은 유저가 메시지를 전송하면") {
                then("ChatRoomAccessDeniedException을 던진다") {
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, senderUuid) } returns null

                    shouldThrow<ChatRoomAccessDeniedException> {
                        sut.handleChatMessage(request, senderUuid)
                    }
                }
            }

            `when`("PENDING 상태 멤버가 메시지를 전송하면") {
                then("ChatRoomAccessDeniedException을 던진다") {
                    val membership = NotificationCenterFixtures.pendingUserParty(party, sender)
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, senderUuid) } returns membership

                    shouldThrow<ChatRoomAccessDeniedException> {
                        sut.handleChatMessage(request, senderUuid)
                    }
                }
            }

            `when`("COMPLETED 상태 파티의 채팅방에 메시지를 전송하면") {
                then("ChatRoomAccessDeniedException을 던진다") {
                    val completedParty = NotificationCenterFixtures.party().also { it.complete() }
                    val membership = NotificationCenterFixtures.hostUserParty(completedParty, sender)
                    every { partyRepository.findByUuid(partyUuid) } returns completedParty
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, senderUuid) } returns membership

                    shouldThrow<ChatRoomAccessDeniedException> {
                        sut.handleChatMessage(request, senderUuid)
                    }
                }
            }
        }
    }
}
