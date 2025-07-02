package com.dogGetDrunk.meetjyou.user.dto

data class TokenResponse(
    val uuid: String,
    val accessToken: String,
    val refreshToken: String
)
