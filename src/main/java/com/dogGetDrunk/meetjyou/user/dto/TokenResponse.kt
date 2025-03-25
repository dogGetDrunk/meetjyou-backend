package com.dogGetDrunk.meetjyou.user.dto

data class TokenResponse(
    val id: Long,
    val accessToken: String,
    val refreshToken: String
)
