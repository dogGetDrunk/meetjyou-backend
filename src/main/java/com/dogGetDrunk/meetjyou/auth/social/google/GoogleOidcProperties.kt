package com.dogGetDrunk.meetjyou.auth.social.google

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth.google")
data class GoogleOidcProperties(
    val issuer: String,
    val clients: Map<String, String>
) {
    val clientIds: List<String>
        get() = clients.values.toList()
}
