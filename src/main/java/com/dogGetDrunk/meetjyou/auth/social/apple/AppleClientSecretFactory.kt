package com.dogGetDrunk.meetjyou.auth.social.apple

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.Date

/**
 * Builds the `client_secret` the Sign in with Apple REST API expects: an ES256 JWT signed with the
 * team's .p8 key. Minted per call — signing is cheap and calls are rare (withdrawal / unlink).
 */
class AppleClientSecretFactory(
    private val props: AppleOidcProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    // Parsed once at startup so a malformed key fails the boot, not the first withdrawal.
    private val signingKey: ECPrivateKey? = if (props.revocationEnabled) parsePrivateKey(props.privateKey) else null

    fun create(): String {
        val key = signingKey ?: error("Apple token revocation is not configured")
        val now = clock.instant()
        val header = JWSHeader.Builder(JWSAlgorithm.ES256).keyID(props.keyId).build()
        val claims = JWTClaimsSet.Builder()
            .issuer(props.teamId)
            .subject(props.clientId)
            .audience(APPLE_AUDIENCE)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(SECRET_TTL)))
            .build()
        val jwt = SignedJWT(header, claims)
        jwt.sign(ECDSASigner(key))
        return jwt.serialize()
    }

    companion object {
        const val APPLE_AUDIENCE = "https://appleid.apple.com"

        // Apple allows up to 6 months; a per-call secret only has to outlive the request.
        val SECRET_TTL: Duration = Duration.ofMinutes(5)

        private val PEM_DECORATION = Regex("-----(BEGIN|END) PRIVATE KEY-----|\\s")

        // Accepts the .p8 file as-is or flattened to one line (the deploy .env holds one line per key).
        fun parsePrivateKey(pem: String): ECPrivateKey {
            val der = Base64.getDecoder().decode(pem.replace(PEM_DECORATION, ""))
            return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der)) as ECPrivateKey
        }
    }
}
