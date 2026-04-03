package com.dogGetDrunk.meetjyou.chat.dto

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageResponse
import java.util.UUID

data class GetChatMessagesResponse(
    val messages: List<ChatMessageResponse>,
    val nextCursor: UUID?,
)
