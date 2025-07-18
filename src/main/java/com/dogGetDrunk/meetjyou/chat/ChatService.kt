package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.message.ChatMessage
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageResponse
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.ChatRoomNotFoundException
import com.dogGetDrunk.meetjyou.user.User
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class ChatService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val messagingTemplate: SimpMessagingTemplate,
) {

    fun handleChatMessage(request: ChatMessageRequest, sender: User) {
        val room = chatRoomRepository.findByUuid(request.roomUuid)
            ?: throw ChatRoomNotFoundException(request.roomUuid.toString())

        val message = ChatMessage(
            room = room,
            sender = sender,
            body = request.message,
        )

        val savedMessage = chatMessageRepository.save(message)

        val response = ChatMessageResponse.of(savedMessage)

        messagingTemplate.convertAndSend("/sub/chat/room/${request.roomUuid}", response)
    }
}
