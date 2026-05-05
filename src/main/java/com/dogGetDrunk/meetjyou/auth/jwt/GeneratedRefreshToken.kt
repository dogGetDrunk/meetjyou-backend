package com.dogGetDrunk.meetjyou.auth.jwt

import java.time.LocalDateTime
import java.util.UUID

data class GeneratedRefreshToken(
    val token: String,
    val jti: UUID,
    val expiresAt: LocalDateTime,
)
