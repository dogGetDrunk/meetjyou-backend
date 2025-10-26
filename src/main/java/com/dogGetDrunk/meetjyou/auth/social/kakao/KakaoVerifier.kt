package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.user.AuthProvider
import org.springframework.stereotype.Component

@Component
class KakaoVerifier(

) : SocialVerifier {

    override fun verifyAndExtract(credential: String): SocialPrincipal {
        return SocialPrincipal(
            provider = AuthProvider.KAKAO,
            subject = "kakao-$credential",
            email = null,
            displayName = null,
        )
    }
}
