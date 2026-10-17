package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

// Every field is optional: Kakao omits the email unless the user consented to share it.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoAccount(
    val email: String? = null,
    val isEmailValid: Boolean? = null,
    val isEmailVerified: Boolean? = null,
) {
    // Only a valid, verified email is kept; anything else is treated as no email.
    fun verifiedEmail(): String? = email?.takeIf { isEmailValid == true && isEmailVerified == true }
}
