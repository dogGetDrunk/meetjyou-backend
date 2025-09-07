package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.connection.ChatSessionTracker
import com.dogGetDrunk.meetjyou.chat.message.ChatMessage
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageResponse
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.ChatRoomNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.event.NotificationEvent
import com.dogGetDrunk.meetjyou.notification.sender.PushNotificationSender
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class ChatService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val userRepository: UserRepository,
    private val userPartyRepository: UserPartyRepository,
    private val pushNotificationSender: PushNotificationSender,
    private val chatSessionTracker: ChatSessionTracker,
    private val publisher: ApplicationEventPublisher,
) {

    fun handleChatMessage(request: ChatMessageRequest, sender: ChatSender) {
        val room = chatRoomRepository.findByUuid(request.roomUuid)
            ?: throw ChatRoomNotFoundException(request.roomUuid.toString())

        val user = userRepository.findByUuid(sender.uuid)
            ?: throw UserNotFoundException(sender.uuid)

        val message = ChatMessage(
            room = room,
            sender = user,
            body = request.message,
        )

        val savedMessage = chatMessageRepository.save(message)
        val response = ChatMessageResponse.of(savedMessage)

        // 실시간 메시지 전송
        messagingTemplate.convertAndSend("/sub/chat/room/${request.roomUuid}", response)

        // 푸시 알림 전송 (sender 자신 제외)
        val participants = userPartyRepository.findAllByParty_Uuid(room.party.uuid)
            .map { it.user }
        participants
            .filter { it.uuid != sender.uuid }
            .filter { connectedUser -> !chatSessionTracker.isUserConnected(room.uuid, connectedUser.uuid) }
            .forEach { receiver ->
                if (receiver.notified) {
                    val payload = NotificationPayload(
                        type = NotificationType.CHAT_MESSAGE,
                        titleArgs = mapOf("senderNickname" to user.nickname),
                        bodyArgs = mapOf("message" to request.message),
                        data = mapOf("type" to "CHAT_MESSAGE", "roomUuid" to room.uuid.toString()),
                        dedupKey = "chat:${room.uuid}${receiver.uuid}",
                    )
                    publisher.publishEvent(NotificationEvent(
                        userUuid = receiver.uuid,
                        payload = payload
                    ))
                }
            }
    }
}
