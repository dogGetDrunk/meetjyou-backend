package com.dogGetDrunk.meetjyou.auth.social

import com.dogGetDrunk.meetjyou.user.AuthProvider

data class SocialPrincipal(
    val provider: AuthProvider,
    val subject: String,
    val email: String?,
    val displayName: String?,
)
