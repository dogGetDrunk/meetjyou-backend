package com.dogGetDrunk.meetjyou.auth.social.kakao

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "auth.kakao")
data class KakaoOidcProperties(
    val issuer: String,
    val jwksUri: String,
    val accessTokenInfoUri: String,
    val userInfoUri: String,
    val clientId: String,
    val appId: String,
    val clockSkewSeconds: Long,
    val jwkCacheTtlMinutes: Long,
    // Login verification calls Kakao inside the user's request, so a hung Kakao must not hold it open.
    val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    val readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) {
    companion object {
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
