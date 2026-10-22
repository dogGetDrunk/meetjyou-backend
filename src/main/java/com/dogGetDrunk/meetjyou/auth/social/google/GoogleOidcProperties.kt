package com.dogGetDrunk.meetjyou.auth.social.google

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth.google")
data class GoogleOidcProperties(
    val issuer: String,
    val clients: Map<String, String>
) {
    // Fail at startup instead of at the first login: the binder keeps an unresolved env
    // placeholder as the literal "${...}" string, so a missing env var would otherwise
    // boot fine and silently reject every id_token for that platform (issue #178).
    init {
        check(clients.isNotEmpty()) { "auth.google.clients must not be empty" }
        val invalidKeys = clients.filterValues(::isUnset).keys
        check(invalidKeys.isEmpty()) {
            "auth.google.clients has blank or unresolved values for: $invalidKeys"
        }
    }

    val clientIds: List<String>
        get() = clients.values.toList()

    private fun isUnset(value: String): Boolean =
        value.isBlank() || value.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)

    companion object {
        private const val UNRESOLVED_PLACEHOLDER_PREFIX = "\${"
    }
}
