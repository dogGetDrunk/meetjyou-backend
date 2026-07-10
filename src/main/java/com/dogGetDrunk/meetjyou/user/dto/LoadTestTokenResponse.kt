package com.dogGetDrunk.meetjyou.user.dto

import java.time.Instant

data class LoadTestTokenResponse(
    val accessToken: String,
    val expiresAt: Instant,
)
