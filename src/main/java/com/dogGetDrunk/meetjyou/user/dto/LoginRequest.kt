package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.user.AuthProvider
import jakarta.validation.constraints.Email

data class LoginRequest(
    @field:Email
    val email: String,
    val authProvider: AuthProvider,
    val credential: String,
)
