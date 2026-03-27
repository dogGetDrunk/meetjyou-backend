package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.connection.ChatSessionTracker
import com.dogGetDrunk.meetjyou.chat.message.ChatMessage
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageResponse
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.chat.ChatMessageTooLongException
import com.dogGetDrunk.meetjyou.common.exception.business.chat.ChatRoomAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.chat.EmptyChatMessageException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.ChatRoomNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.event.NotificationEvent
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ChatService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val userRepository: UserRepository,
    private val userPartyRepository: UserPartyRepository,
    private val chatSessionTracker: ChatSessionTracker,
    private val chatParticipantService: ChatParticipantService,
    private val publisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(ChatService::class.java)

    fun handleChatMessage(request: ChatMessageRequest, senderUuid: UUID) {
        log.info("Chat message send requested. roomUuid={}, senderUuid={}", request.roomUuid, senderUuid)

        val room = chatRoomRepository.findByUuid(request.roomUuid)
            ?: throw ChatRoomNotFoundException(request.roomUuid.toString())

        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(request.roomUuid)
            ?: throw ChatRoomNotFoundException(request.roomUuid.toString())

        val sender = userRepository.findByUuid(senderUuid)
            ?: throw UserNotFoundException(senderUuid)

        validateMessageSendPermission(partyUuid, senderUuid)

        val normalizedMessage = request.message.trim()
        validateMessageBody(normalizedMessage)

        val savedMessage = chatMessageRepository.save(
            ChatMessage(
                room = room,
                sender = sender,
                body = normalizedMessage,
            )
        )

        log.info(
            "Chat message persisted successfully. roomUuid={}, messageUuid={}, senderUuid={}",
            room.uuid,
            savedMessage.uuid,
            senderUuid,
        )

        val response = ChatMessageResponse.of(savedMessage)

        messagingTemplate.convertAndSend("/sub/chat/room/${room.uuid}", response)

        log.info(
            "Chat message broadcast completed. roomUuid={}, messageUuid={}",
            room.uuid,
            savedMessage.uuid,
        )

        val readUsers = chatSessionTracker.getConnectedUsers(room.uuid).toMutableSet().apply {
            add(senderUuid)
        }

        chatParticipantService.markReadForUsers(
            roomUuid = room.uuid,
            userUuids = readUsers,
            readAt = savedMessage.createdAt,
        )

        val receivers = userPartyRepository.findAllWithUserByPartyUuid(partyUuid)
            .map { it.user }
            .filter { it.uuid != senderUuid }
            .filter { receiver -> !chatSessionTracker.isUserConnected(room.uuid, receiver.uuid) }

        log.info(
            "Notification receivers resolved for chat message. roomUuid={}, senderUuid={}, receiverCount={}",
            room.uuid,
            senderUuid,
            receivers.size,
        )

        receivers.forEach { receiver ->
            if (receiver.notified) {
                val payload = NotificationPayload(
                    type = NotificationType.CHAT_MESSAGE,
                    titleArgs = mapOf("senderNickname" to sender.nickname),
                    bodyArgs = mapOf("message" to normalizedMessage),
                    data = mapOf(
                        "type" to "CHAT_MESSAGE",
                        "roomUuid" to room.uuid.toString(),
                    ),
                    dedupKey = "chat:${room.uuid}:${receiver.uuid}",
                )

                publisher.publishEvent(
                    NotificationEvent(
                        userUuid = receiver.uuid,
                        payload = payload,
                    )
                )

                log.debug(
                    "Chat notification event published. roomUuid={}, receiverUuid={}, senderUuid={}",
                    room.uuid,
                    receiver.uuid,
                    senderUuid,
                )
            }
        }
    }

    private fun validateMessageSendPermission(partyUuid: UUID, senderUuid: UUID) {
        val hasPermission = userPartyRepository.existsByParty_UuidAndUser_UuidAndMemberStatus(
            partyUuid = partyUuid,
            userUuid = senderUuid,
            memberStatus = MemberStatus.JOINED,
        )

        if (!hasPermission) {
            log.warn(
                "User does not have permission to send message to this chat room. partyUuid={}, senderUuid={}",
                partyUuid,
                senderUuid,
            )
            throw ChatRoomAccessDeniedException(senderUuid.toString())
        }
    }

    private fun validateMessageBody(message: String) {
        if (message.isBlank()) {
            log.warn("Empty chat message attempt detected.")
            throw EmptyChatMessageException(message)
        }

        if (message.length > 1000) {
            log.warn("Chat message length exceeded the limit. length={}", message.length)
            throw ChatMessageTooLongException(message.length.toString())
        }
    }
}
