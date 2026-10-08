package com.dogGetDrunk.meetjyou.user.dto

import jakarta.validation.constraints.NotBlank

// The refresh token travels in the body, never in the Authorization header: that header is
// reserved for access tokens (RFC 6750 §2.1), and refresh tokens go only to the token endpoints
// (RFC 6749 §1.5, RFC 7009 §2.1).
data class RefreshTokenRequest(
    @field:NotBlank
    val refreshToken: String,
)
