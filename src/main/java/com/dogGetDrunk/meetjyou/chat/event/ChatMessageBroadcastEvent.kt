package com.dogGetDrunk.meetjyou.chat.event

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageResponse
import java.util.UUID

data class ChatMessageBroadcastEvent(
    val roomUuid: UUID,
    val message: ChatMessageResponse,
)
