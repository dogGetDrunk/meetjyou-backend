package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.auth.ChatSender
import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller

@Controller
class ChatController(
    private val chatService: ChatService,
) {

    @MessageMapping("/chat/message")
    fun handleMessage(request: ChatMessageRequest) {
        val userPrincipal = SecurityContextHolder.getContext().authentication.principal as CustomUserPrincipal

        chatService.handleChatMessage(request, ChatSender(userPrincipal.uuid, userPrincipal.username))
    }
}
