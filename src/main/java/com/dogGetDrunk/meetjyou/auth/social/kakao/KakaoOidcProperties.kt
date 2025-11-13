package com.dogGetDrunk.meetjyou.auth.social.kakao

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth.kakao")
data class KakaoOidcProperties(
    val issuer: String,
    val jwksUri: String,
    val accessTokenInfoUri: String,
    val userInfoUri: String,
    val clientId: String,
    val appId: String,
    val jwkCacheTtlMinutes: Long,
)
