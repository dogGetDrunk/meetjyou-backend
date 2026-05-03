package com.dogGetDrunk.meetjyou.auth.social.apple

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth.apple")
data class AppleOidcProperties(
    val issuer: String,
    val jwksUri: String,
    val clientId: String,
    val clockSkewSeconds: Long,
    val jwkCacheTtlMinutes: Long,
)
