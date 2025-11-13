package com.dogGetDrunk.meetjyou.auth.social.google

import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialToken
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.springframework.stereotype.Component

@Component
class GoogleVerifier(
    private val firebaseApp: FirebaseApp,
) : SocialVerifier {

    override fun verifyAndExtract(token: SocialToken, nonce: String?): SocialPrincipal {
        require(token is IdToken) { "GoogleVerifier는 IdToken만 처리할 수 있습니다." }
        val auth = FirebaseAuth.getInstance(firebaseApp)
        val decoded = auth.verifyIdToken(token.value, true)
        return SocialPrincipal(
            authProvider = AuthProvider.GOOGLE,
            subject = decoded.uid,
            email = decoded.email,
        )
    }
}
