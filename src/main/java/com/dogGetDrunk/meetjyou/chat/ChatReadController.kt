package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.dto.GetChatMessagesResponse
import com.dogGetDrunk.meetjyou.chat.dto.GetChatRoomsResponse
import com.dogGetDrunk.meetjyou.chat.dto.GetUnreadCountResponse
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ChatReadController(
    private val chatReadService: ChatReadService,
) {

    @GetMapping("/api/v1/chat/rooms/{roomUuid}/messages")
    fun getMessages(
        @PathVariable roomUuid: UUID,
        @RequestParam(required = false) beforeMessageUuid: UUID?,
        @RequestParam(required = false, defaultValue = "30") size: Int,
    ): ResponseEntity<GetChatMessagesResponse> {
        val requesterUuid = SecurityUtil.getCurrentUserUuid()

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
    ): ResponseEntity<GetUnreadCountResponse> {
        val requesterUuid = SecurityUtil.getCurrentUserUuid()

        val unreadCount = chatReadService.getUnreadCount(
            roomUuid = roomUuid,
            requesterUuid = requesterUuid,
        )

        return ResponseEntity.ok(GetUnreadCountResponse(unreadCount = unreadCount))
    }

    @GetMapping("/api/v1/chat/rooms")
    fun getChatRooms(): ResponseEntity<GetChatRoomsResponse> {
        val requesterUuid = SecurityUtil.getCurrentUserUuid()

        val response = chatReadService.getChatRooms(
            requesterUuid = requesterUuid,
        )

        return ResponseEntity.ok(response)
    }

    @PostMapping("/api/v1/chat/rooms/{roomUuid}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAsRead(
        @PathVariable roomUuid: UUID,
        @RequestParam(required = false) messageUuid: UUID?,
    ) {
        val requesterUuid = SecurityUtil.getCurrentUserUuid()

        chatReadService.markAsRead(
            roomUuid = roomUuid,
            requesterUuid = requesterUuid,
            messageUuid = messageUuid,
        )
    }
}
