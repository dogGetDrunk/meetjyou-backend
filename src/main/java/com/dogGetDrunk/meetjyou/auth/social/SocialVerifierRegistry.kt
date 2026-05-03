package com.dogGetDrunk.meetjyou.auth.social

import com.dogGetDrunk.meetjyou.auth.social.apple.AppleVerifier
import com.dogGetDrunk.meetjyou.auth.social.google.GoogleVerifier
import com.dogGetDrunk.meetjyou.auth.social.kakao.KakaoVerifier
import com.dogGetDrunk.meetjyou.user.AuthProvider
import org.springframework.stereotype.Component

@Component
class SocialVerifierRegistry(
    googleVerifier: GoogleVerifier,
    kakaoVerifier: KakaoVerifier,
    appleVerifier: AppleVerifier,
) {
    private val delegates: Map<AuthProvider, SocialVerifier> = mapOf(
        AuthProvider.GOOGLE to googleVerifier,
        AuthProvider.KAKAO to kakaoVerifier,
        AuthProvider.APPLE to appleVerifier,
    )

    fun get(provider: AuthProvider): SocialVerifier =
        delegates[provider] ?: error("Unsupported provider: $provider")
}
