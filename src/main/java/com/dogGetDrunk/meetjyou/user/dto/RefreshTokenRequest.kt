package com.dogGetDrunk.meetjyou.user.dto

import jakarta.validation.constraints.Email
import java.util.UUID

data class RefreshTokenRequest(
    val uuid: UUID,
    @field:Email
    val email: String,
)
