package com.dogGetDrunk.meetjyou.auth.social

sealed interface SocialToken {
    val value: String
}

@JvmInline
value class IdToken(override val value: String) : SocialToken

@JvmInline
value class AccessToken(override val value: String) : SocialToken
