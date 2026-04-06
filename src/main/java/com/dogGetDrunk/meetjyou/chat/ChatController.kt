package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.security.Principal
import java.util.UUID

@Controller
class ChatController(
    private val chatService: ChatService,
) {

    @MessageMapping("/chat/message")
    fun handleMessage(
        request: ChatMessageRequest,
        principal: Principal?,
    ) {
        val senderUuid = principal?.name
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw IllegalStateException("Authenticated chat principal is required.")

        chatService.handleChatMessage(request, senderUuid)
    }
}
