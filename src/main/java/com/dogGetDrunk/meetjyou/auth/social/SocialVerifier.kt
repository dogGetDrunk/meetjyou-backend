package com.dogGetDrunk.meetjyou.auth.social

interface SocialVerifier {
    fun verifyAndExtract(credential: String?, accessToken: String?): SocialPrincipal
}
