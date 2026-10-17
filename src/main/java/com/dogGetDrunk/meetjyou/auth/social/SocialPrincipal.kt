package com.dogGetDrunk.meetjyou.auth.social

import com.dogGetDrunk.meetjyou.user.AuthProvider

data class SocialPrincipal(
    val authProvider: AuthProvider,
    val subject: String,
    // Null when the provider shares no verified email (not consented, or unverified).
    val email: String?,
)
