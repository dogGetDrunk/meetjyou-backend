package com.dogGetDrunk.meetjyou.user.dto

/** [created] is false when the identity was already linked to the caller (a retried request). */
data class SocialAccountLinkResult(
    val account: SocialAccountResponse,
    val created: Boolean,
)
