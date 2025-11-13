package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialToken
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.AuthProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class KakaoVerifier(
    private val props: KakaoOidcProperties,
    @Qualifier("kakaoJwtDecoder") private val kakaoJwtDecoder: JwtDecoder,
) : SocialVerifier {

    override fun verifyAndExtract(token: SocialToken, nonce: String?): SocialPrincipal {
        // https://developers.kakao.com/docs/latest/ko/kakaologin/utilize#oidc-id-token-verify
        return when (token) {
            is IdToken -> verifyIdToken(token)
            is AccessToken -> verifyAccessToken(token)
        }
    }

    private fun verifyIdToken(token: IdToken, nonce: String? = null): SocialPrincipal {
        val verifiedJwt = kakaoJwtDecoder.decode(token.value)
        if (!nonce.isNullOrBlank() && verifiedJwt.claims["nonce"] != nonce) {
            throw InvalidJwtException()
        }
        return SocialPrincipal(
            authProvider = AuthProvider.KAKAO,
            subject = verifiedJwt.subject,
            email = verifiedJwt.claims["email"] as? String ?: throw InvalidJwtException()
        )
    }

    private fun verifyAccessToken(token: AccessToken): SocialPrincipal {
        val tokenInfo: KakaoAccessTokenInfo = runCatching {
            RestClient.create(props.accessTokenInfoUri)
                .get()
                .headers {
                    it.setBearerAuth(token.value)
                    it.contentType = MediaType.APPLICATION_FORM_URLENCODED
                }
                .retrieve()
                .toEntity(KakaoAccessTokenInfo::class.java)
                .body ?: throw InvalidJwtException()
        }.getOrElse {
            // Fail to request: Kakao access token info api
            throw Exception()
        }

        if (tokenInfo.appId.toString() != props.clientId) {
            throw InvalidJwtException()
        }

        val userInfo: KakaoUserInfo = runCatching {
            RestClient.create(props.userInfoUri)
                .get()
                .headers {
                    it.setBearerAuth(token.value)
                    it.contentType = MediaType.APPLICATION_FORM_URLENCODED
                }
                .retrieve()
                .toEntity(KakaoUserInfo::class.java)
                .body ?: throw InvalidJwtException()
        }.getOrElse {
            // Fail to request: Kakao user info api
            throw Exception()
        }

        val subject = tokenInfo.id.toString()
        val email = userInfo.kakaoAccount.email

        return SocialPrincipal(
            authProvider = AuthProvider.KAKAO,
            subject = subject,
            email = email
        )
    }
}
