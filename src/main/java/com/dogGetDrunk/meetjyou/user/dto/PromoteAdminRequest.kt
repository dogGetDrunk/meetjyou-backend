package com.dogGetDrunk.meetjyou.user.dto

import jakarta.validation.constraints.NotBlank

data class PromoteAdminRequest(
    @field:NotBlank
    val passphrase: String,
)
