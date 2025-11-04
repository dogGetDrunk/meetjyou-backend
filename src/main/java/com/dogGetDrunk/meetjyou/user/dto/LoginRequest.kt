package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.user.AuthProvider
import jakarta.validation.constraints.AssertTrue

data class LoginRequest(
    val authProvider: AuthProvider,
    val credential: String? = null,
    val accessToken: String? = null,
) {
    @AssertTrue(message = "credential 또는 accessToken 중 하나는 반드시 존재해야 합니다.")
    private fun isValidAuthentication(): Boolean =
        !(credential.isNullOrBlank() && accessToken.isNullOrBlank())
}
