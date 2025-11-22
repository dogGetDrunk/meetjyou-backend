package com.dogGetDrunk.meetjyou.image.cloud.oracle.dto

import java.time.LocalDateTime

data class ParResponse(
    val parUrl: String,
    val httpMethod: String,
    val expiresAt: LocalDateTime
)
