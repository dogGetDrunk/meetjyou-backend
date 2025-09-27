package com.dogGetDrunk.meetjyou.user.dto

import jakarta.validation.constraints.Email
import java.util.UUID

data class LoginRequest(
    val uuid: UUID,
    @field:Email
    val email: String,
)
