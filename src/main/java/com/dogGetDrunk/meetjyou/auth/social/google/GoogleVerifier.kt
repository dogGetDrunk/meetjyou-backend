package com.dogGetDrunk.meetjyou.auth.social.google

import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.springframework.stereotype.Component

@Component
class GoogleVerifier(
    private val firebaseApp: FirebaseApp,
) : SocialVerifier {

    override fun verifyAndExtract(credential: String): SocialPrincipal {
        val auth = FirebaseAuth.getInstance(firebaseApp)
        val decoded = auth.verifyIdToken(credential, true)
        return SocialPrincipal(
            provider = AuthProvider.GOOGLE,
            subject = decoded.uid,
            email = decoded.email,
            displayName = decoded.name,
        )
    }
}
