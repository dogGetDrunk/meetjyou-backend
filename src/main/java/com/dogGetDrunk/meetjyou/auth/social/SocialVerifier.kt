package com.dogGetDrunk.meetjyou.auth.social

interface SocialVerifier {
    fun verifyAndExtract(token: SocialToken, nonce: String? = null): SocialPrincipal
}
