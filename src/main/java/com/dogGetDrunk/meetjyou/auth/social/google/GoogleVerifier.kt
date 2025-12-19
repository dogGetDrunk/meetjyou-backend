package com.dogGetDrunk.meetjyou.auth.social.google

import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialToken
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import org.springframework.stereotype.Component

@Component
class GoogleVerifier(
    private val googleIdTokenVerifier: GoogleIdTokenVerifier
) : SocialVerifier {

    override fun verifyAndExtract(token: SocialToken, nonce: String?): SocialPrincipal {
        return when (token) {
            is IdToken -> verifyIdToken(token)
            is AccessToken -> throw InvalidJwtException(message = "Google Access Token verification is not supported.")
        }
    }

    fun verifyIdToken(token: IdToken): SocialPrincipal {
        // TODO: 시간 측정 코드 추가
        val verifiedJwt = googleIdTokenVerifier.verify(token.value)
            ?: throw InvalidJwtException()

        val payload = verifiedJwt.payload
        val subject = payload.subject ?: throw InvalidJwtException()
        val email = payload.email ?: throw InvalidJwtException()

        return SocialPrincipal(
            authProvider = AuthProvider.GOOGLE,
            subject = subject,
            email = email
        )
    }
}
