package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.UserSocialAccount
import java.time.Instant

data class SocialAccountResponse(
    val provider: AuthProvider,
    val email: String,
    val linkedAt: Instant,
) {
    companion object {
        fun of(account: UserSocialAccount): SocialAccountResponse =
            SocialAccountResponse(
                provider = account.provider,
                email = account.email,
                linkedAt = account.createdAt,
            )
    }
}
