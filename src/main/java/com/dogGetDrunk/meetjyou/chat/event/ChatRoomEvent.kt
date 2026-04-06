package com.dogGetDrunk.meetjyou.chat.event

import java.util.UUID

data class ChatRoomEvent(
    val type: ChatRoomEventType,
    val roomUuid: UUID,
    val partyUuid: UUID,
    val actorUserUuid: UUID? = null,
    val targetUserUuid: UUID? = null,
    val readerUserUuid: UUID? = null,
    val previousLastReadMessageId: Long? = null,
    val currentLastReadMessageId: Long? = null,
)
