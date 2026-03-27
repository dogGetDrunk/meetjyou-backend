package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class ChatController(
    private val chatService: ChatService,
    private val environment: Environment,
) {

    private val log = LoggerFactory.getLogger(ChatController::class.java)

    @MessageMapping("/chat/message")
    fun handleMessage(
        request: ChatMessageRequest,
        @Header("X-Debug-User-UUID", required = false) debugUserUuidHeader: String?,
    ) {
        val senderUuid = resolveSenderUuid(debugUserUuidHeader)

        chatService.handleChatMessage(request, senderUuid)
    }

    private fun resolveSenderUuid(debugUserUuidHeader: String?): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as? CustomUserPrincipal

        if (principal != null) {
            return principal.uuid
        }

        if (isDevProfile()) {
            val debugUserUuid = debugUserUuidHeader
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw IllegalStateException("Debug user UUID is required in dev when authentication is missing.")

            log.warn("Debug user UUID was used because authentication was missing. userUuid={}", debugUserUuid)
            return debugUserUuid
        }

        throw IllegalStateException("Authenticated principal is required.")
    }

    private fun isDevProfile(): Boolean {
        return environment.activeProfiles.contains("dev")
    }
}
