package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.jwt.UserContext
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@Controller
class ChatController(
    private val chatService: ChatService,
) {

    @MessageMapping("/chat/message")
    fun handleMessage(request: ChatMessageRequest) {
        val sender = UserContext.getUser()
        chatService.handleChatMessage(request, sender)
    }
}
