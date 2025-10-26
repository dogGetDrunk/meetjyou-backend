package com.dogGetDrunk.meetjyou.auth.social

import com.dogGetDrunk.meetjyou.auth.social.google.GoogleVerifier
import com.dogGetDrunk.meetjyou.auth.social.kakao.KakaoVerifier
import com.dogGetDrunk.meetjyou.user.AuthProvider
import org.springframework.stereotype.Component

@Component
class SocialVerifierRegistry(
    googleVerifier: GoogleVerifier,
    kakaoVerifier: KakaoVerifier,
) {
    private val delegates: Map<AuthProvider, SocialVerifier> = mapOf(
        AuthProvider.GOOGLE to googleVerifier,
        AuthProvider.KAKAO to kakaoVerifier,
//        AuthProvider.APPLE to error("AppleVerifier is not implemented yet")
    )

    fun get(provider: AuthProvider): SocialVerifier =
        delegates[provider] ?: error("Unsupported provider: $provider")
}
