package com.dogGetDrunk.meetjyou.cloud.oracle.dto

import java.time.Instant

data class ParResponse(
    val url: String,
    val httpMethod: String,
    val expiresAt: Instant,
)
