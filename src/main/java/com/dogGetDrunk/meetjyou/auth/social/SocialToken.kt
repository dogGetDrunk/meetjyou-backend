package com.dogGetDrunk.meetjyou.auth.social

import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException

sealed interface SocialToken {
    val value: String
}

@JvmInline
value class IdToken(override val value: String) : SocialToken

@JvmInline
value class AccessToken(override val value: String) : SocialToken

/** Prefers the id_token; a client that only obtained an access token (Kakao SDK) sends that instead. */
fun socialTokenOf(idToken: String?, accessToken: String?): SocialToken =
    if (!idToken.isNullOrBlank()) {
        IdToken(idToken)
    } else {
        AccessToken(accessToken ?: throw InvalidJwtException(message = "No idToken or accessToken provided"))
    }
