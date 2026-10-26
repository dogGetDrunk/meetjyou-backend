package com.dogGetDrunk.meetjyou.user

import java.util.UUID

/**
 * Published when a user is cut off from every device (withdrawal). Modules holding live connections
 * (chat WebSockets) close them after commit, so the user module never depends on them.
 */
data class UserSessionsRevokedEvent(
    val userUuid: UUID,
)
