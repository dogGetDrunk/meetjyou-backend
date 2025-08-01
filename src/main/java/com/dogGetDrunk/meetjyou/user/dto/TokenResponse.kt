package com.dogGetDrunk.meetjyou.user.dto

import java.util.UUID

data class TokenResponse(
    val uuid: UUID,
    val email: String,
    val accessToken: String,
    val refreshToken: String
)
