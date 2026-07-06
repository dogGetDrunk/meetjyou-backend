package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.dto.GetChatMessagesResponse
import com.dogGetDrunk.meetjyou.chat.dto.GetChatRoomsResponse
import com.dogGetDrunk.meetjyou.chat.dto.GetUnreadCountResponse
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.UUID

@RestControllerV1
@RequestMapping("/chat")
class ChatReadController(
    private val chatReadService: ChatReadService,
) {

    @GetMapping("/rooms/{roomUuid}/messages")
    fun getMessages(
        @PathVariable roomUuid: UUID,
        @RequestParam(required = false) beforeMessageUuid: UUID?,
        @RequestParam(required = false, defaultValue = "30") size: Int,
    ): ResponseEntity<GetChatMessagesResponse> {
        val response = chatReadService.getMessages(
            roomUuid = roomUuid,
            beforeMessageUuid = beforeMessageUuid,
            size = size,
        )

        return ResponseEntity.ok(response)
    }

    @GetMapping("/rooms/{roomUuid}/unread-count")
    fun getUnreadCount(
        @PathVariable roomUuid: UUID,
    ): ResponseEntity<GetUnreadCountResponse> {
        val unreadCount = chatReadService.getUnreadCount(roomUuid = roomUuid)

        return ResponseEntity.ok(GetUnreadCountResponse(unreadCount = unreadCount))
    }

    @GetMapping("/rooms")
    fun getChatRooms(): ResponseEntity<GetChatRoomsResponse> {
        val response = chatReadService.getChatRooms()

        return ResponseEntity.ok(response)
    }

    @PostMapping("/rooms/{roomUuid}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAsRead(
        @PathVariable roomUuid: UUID,
        @RequestParam(required = false) messageUuid: UUID?,
    ) {
        chatReadService.markAsRead(
            roomUuid = roomUuid,
            messageUuid = messageUuid,
        )
    }
}
