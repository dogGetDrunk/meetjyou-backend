package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.chat.dto.GetChatMessagesResponse
import com.dogGetDrunk.meetjyou.chat.dto.GetChatRoomsResponse
import com.dogGetDrunk.meetjyou.chat.dto.GetUnreadCountResponse
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ChatReadController(
    private val chatReadService: ChatReadService,
    private val environment: Environment,
) {

    private val log = LoggerFactory.getLogger(ChatReadController::class.java)

    @GetMapping("/api/v1/chat/rooms/{roomUuid}/messages")
    fun getMessages(
        @PathVariable roomUuid: UUID,
        @RequestParam(required = false) beforeMessageUuid: UUID?,
        @RequestParam(required = false, defaultValue = "30") size: Int,
        @RequestHeader("X-Debug-User-UUID", required = false) debugUserUuidHeader: String?,
    ): ResponseEntity<GetChatMessagesResponse> {
        val requesterUuid = resolveRequesterUuid(debugUserUuidHeader)

        val response = chatReadService.getMessages(
            roomUuid = roomUuid,
            requesterUuid = requesterUuid,
            beforeMessageUuid = beforeMessageUuid,
            size = size,
        )

        return ResponseEntity.ok(response)
    }

    @GetMapping("/api/v1/chat/rooms/{roomUuid}/unread-count")
    fun getUnreadCount(
        @PathVariable roomUuid: UUID,
        @RequestHeader("X-Debug-User-UUID", required = false) debugUserUuidHeader: String?,
    ): ResponseEntity<GetUnreadCountResponse> {
        val requesterUuid = resolveRequesterUuid(debugUserUuidHeader)

        val unreadCount = chatReadService.getUnreadCount(
            roomUuid = roomUuid,
            requesterUuid = requesterUuid,
        )

        return ResponseEntity.ok(GetUnreadCountResponse(unreadCount = unreadCount))
    }

    @GetMapping("/api/v1/chat/rooms")
    fun getChatRooms(
        @RequestHeader("X-Debug-User-UUID", required = false) debugUserUuidHeader: String?,
    ): ResponseEntity<GetChatRoomsResponse> {
        val requesterUuid = resolveRequesterUuid(debugUserUuidHeader)

        val response = chatReadService.getChatRooms(
            requesterUuid = requesterUuid,
        )

        return ResponseEntity.ok(response)
    }

    private fun resolveRequesterUuid(debugUserUuidHeader: String?): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as? CustomUserPrincipal

        if (principal != null) {
            return principal.uuid
        }

        if (isDevProfile()) {
            val debugUserUuid = debugUserUuidHeader
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw IllegalStateException("Debug user UUID is required in dev when authentication is missing.")

            log.warn("Debug user UUID was used for chat read because authentication was missing. userUuid={}", debugUserUuid)
            return debugUserUuid
        }

        throw IllegalStateException("Authenticated principal is required.")
    }

    private fun isDevProfile(): Boolean {
        return environment.activeProfiles.contains("dev")
    }
}
