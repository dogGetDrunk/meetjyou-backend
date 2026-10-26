package com.dogGetDrunk.meetjyou.chat.connection

import java.util.UUID

/** [userUuid] lost access to [roomUuid]; its open sessions there get closed after commit with [reason]. */
data class ChatAccessRevokedEvent(
    val roomUuid: UUID,
    val userUuid: UUID,
    val reason: ChatAccessRevocationReason,
)
