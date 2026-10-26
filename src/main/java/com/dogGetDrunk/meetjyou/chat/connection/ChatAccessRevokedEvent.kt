package com.dogGetDrunk.meetjyou.chat.connection

import java.util.UUID

/** [userUuid] lost access to [roomUuid] (kicked or left); its open sessions there get closed after commit. */
data class ChatAccessRevokedEvent(
    val roomUuid: UUID,
    val userUuid: UUID,
)
