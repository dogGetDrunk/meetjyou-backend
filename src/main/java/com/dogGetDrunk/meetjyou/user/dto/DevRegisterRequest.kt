package com.dogGetDrunk.meetjyou.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class DevRegisterRequest(
    @field:Email
    val email: String,
    @field:Size(min = 2, max = 8)
    @field:NotBlank
    @field:Pattern(regexp = "^[a-zA-Z0-9가-힣_]+$")
    val nickname: String,
)
