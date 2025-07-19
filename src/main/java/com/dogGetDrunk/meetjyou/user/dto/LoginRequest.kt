package com.dogGetDrunk.meetjyou.user.dto

import java.util.UUID

data class LoginRequest(
    val uuid: UUID,
    val email: String,
)
