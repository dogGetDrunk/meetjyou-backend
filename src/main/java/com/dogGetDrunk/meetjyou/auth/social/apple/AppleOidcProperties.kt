package com.dogGetDrunk.meetjyou.auth.social.apple

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth.apple")
data class AppleOidcProperties(
    val issuer: String,
    val jwksUri: String,
    val clientId: String,
    val clockSkewSeconds: Long,
    val jwkCacheTtlMinutes: Long,
    // Sign in with Apple REST API credentials (team id, key id, .p8 private key) used to revoke a
    // user's authorization on withdrawal (#171). All blank = revocation disabled, so the app still
    // boots before the key is issued; partially set = misconfiguration, rejected at startup.
    val teamId: String = "",
    val keyId: String = "",
    val privateKey: String = "",
) {
    init {
        val revocationCredentials = listOf(teamId, keyId, privateKey)
        check(revocationCredentials.all(::isUnset) || revocationCredentials.none(::isUnset)) {
            "auth.apple.team-id, key-id and private-key must be set together or all left blank"
        }
    }

    val revocationEnabled: Boolean
        get() = !isUnset(privateKey)

    private fun isUnset(value: String): Boolean =
        value.isBlank() || value.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)

    companion object {
        private const val UNRESOLVED_PLACEHOLDER_PREFIX = "\${"
    }
}
