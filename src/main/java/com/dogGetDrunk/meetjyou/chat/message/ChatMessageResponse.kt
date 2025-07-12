package com.dogGetDrunk.meetjyou.chat.message

import java.time.LocalDateTime

data class ChatMessageResponse(
    val uuid: String,
    val roomUuid: String,
    val senderUuid: String,
    val senderNickname: String,
    val body: String,
    val createdAt: LocalDateTime,
    val isMine: Boolean
)
