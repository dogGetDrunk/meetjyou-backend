package com.dogGetDrunk.meetjyou.chat.message

import com.dogGetDrunk.meetjyou.user.dto.UserDisplay
import java.time.Instant

data class ChatMessageResponse(
    val messageId: Long,
    val uuid: String,
    val roomUuid: String,
    val senderUuid: String,
    val senderNickname: String,
    // Whether senderNickname is the withdrawn placeholder (#207).
    val isWithdrawn: Boolean,
    val body: String,
    val createdAt: Instant,
    val unreadCount: Long,
) {
    companion object {
        // Shared by history reads and the live broadcast, so both show the sender the same way.
        fun of(chatMessage: ChatMessage, unreadCount: Long): ChatMessageResponse {
            val sender = UserDisplay.of(chatMessage.sender)
            return ChatMessageResponse(
                messageId = chatMessage.id,
                uuid = chatMessage.uuid.toString(),
                roomUuid = chatMessage.room.uuid.toString(),
                senderUuid = chatMessage.sender.uuid.toString(),
                senderNickname = sender.nickname,
                isWithdrawn = sender.isWithdrawn,
                body = chatMessage.body,
                createdAt = chatMessage.createdAt,
                unreadCount = unreadCount,
            )
        }
    }
}
