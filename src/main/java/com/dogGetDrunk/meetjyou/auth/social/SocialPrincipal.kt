package com.dogGetDrunk.meetjyou.auth.social

import com.dogGetDrunk.meetjyou.user.AuthProvider

data class SocialPrincipal(
    val authProvider: AuthProvider,
    val subject: String,
    val email: String,
    val displayName: String?,
)
