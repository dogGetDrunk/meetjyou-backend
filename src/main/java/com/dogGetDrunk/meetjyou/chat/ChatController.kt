package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class ChatController(
    private val chatService: ChatService,
) {

    @MessageMapping("/chat/message")
    fun handleMessage(
        request: ChatMessageRequest,
        headerAccessor: SimpMessageHeaderAccessor,
    ) {
        val senderUuid = headerAccessor.sessionAttributes?.get("userUuid") as? UUID
            ?: throw IllegalStateException("Authenticated chat principal is required.")

        chatService.handleChatMessage(request, senderUuid)
    }
}
