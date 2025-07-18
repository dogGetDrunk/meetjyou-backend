package com.dogGetDrunk.meetjyou.chat.message

import java.time.LocalDateTime

data class ChatMessageResponse(
    val uuid: String,
    val roomUuid: String,
    val senderUuid: String,
    val senderNickname: String,
    val body: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun of(chatMessage: ChatMessage): ChatMessageResponse {
            return ChatMessageResponse(
                uuid = chatMessage.uuid.toString(),
                roomUuid = chatMessage.room.uuid.toString(),
                senderUuid = chatMessage.sender.uuid.toString(),
                senderNickname = chatMessage.sender.nickname,
                body = chatMessage.body,
                createdAt = chatMessage.createdAt,
            )
        }
    }
}
