package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.user.AuthProvider
import jakarta.validation.constraints.Email
import java.util.UUID

data class LoginRequest(
    val uuid: UUID,
    @field:Email
    val email: String,
    val authProvider: AuthProvider,
    val credential: String,
)
