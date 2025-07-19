package com.dogGetDrunk.meetjyou.chat.message

import java.util.UUID

// 클라이언트가 /pub/chat/message 요청을 보낼 때 사용하는 DTO
data class ChatMessageRequest(
    val roomUuid: UUID,
    val message: String,
)
