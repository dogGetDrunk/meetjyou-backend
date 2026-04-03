package com.dogGetDrunk.meetjyou.chat.dto

import java.time.Instant
import java.util.UUID

data class ChatRoomSummaryResponse(
    val roomUuid: UUID,
    val partyUuid: UUID,
    val partyName: String,
    val lastMessage: String?,
    val lastMessageAt: Instant?,
    val unreadCount: Long,
)
