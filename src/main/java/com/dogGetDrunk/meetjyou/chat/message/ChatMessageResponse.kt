package com.dogGetDrunk.meetjyou.chat.message

import java.time.Instant

data class ChatMessageResponse(
    val messageId: Long,
    val uuid: String,
    val roomUuid: String,
    val senderUuid: String,
    val senderNickname: String,
    val body: String,
    val createdAt: Instant,
    val unreadCount: Long,
) {
    companion object {
        fun of(chatMessage: ChatMessage, unreadCount: Long): ChatMessageResponse {
            return ChatMessageResponse(
                messageId = chatMessage.id,
                uuid = chatMessage.uuid.toString(),
                roomUuid = chatMessage.room.uuid.toString(),
                senderUuid = chatMessage.sender.uuid.toString(),
                senderNickname = chatMessage.sender.nickname,
                body = chatMessage.body,
                createdAt = chatMessage.createdAt,
                unreadCount = unreadCount,
            )
        }
    }
}
