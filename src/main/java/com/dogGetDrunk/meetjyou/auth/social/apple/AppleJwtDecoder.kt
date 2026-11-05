package com.dogGetDrunk.meetjyou.auth.social.apple

import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.util.Date

class AppleJwtDecoder(
    private val props: AppleOidcProperties,
    private val jwksCache: AppleJwksCache,
) : JwtDecoder {

    private val log = LoggerFactory.getLogger(javaClass)

    // The signature is verified before any claim is read: unverified claims must not steer the outcome
    // (e.g. a forged token reported as "expired" instead of as a bad signature).
    override fun decode(token: String): Jwt {
        val signed = parse(token)
        verifySignature(signed)

        val claims = signed.jwtClaimsSet
        validateIssuer(claims)
        validateAudience(claims)
        val expirationTime = requireClaim(claims.expirationTime, CLAIM_EXP)
        validateNotExpired(expirationTime)
        val issueTime = requireClaim(claims.issueTime, CLAIM_IAT)
        requireClaim(claims.subject?.takeIf { it.isNotBlank() }, CLAIM_SUB)

        val headers = signed.header.toJSONObject()
        val jwtClaims = claims.claims
        return Jwt.withTokenValue(token)
            .headers { h -> h.putAll(headers) }
            .claims { c -> c.putAll(jwtClaims) }
            .issuedAt(issueTime.toInstant())
            .expiresAt(expirationTime.toInstant())
            .build()
    }

    private fun parse(token: String): SignedJWT =
        try {
            SignedJWT.parse(token)
        } catch (e: Exception) {
            log.warn("Apple JWT parsing failed", e)
            throw InvalidJwtException()
        }

    // ExternalServiceException from the JWKS lookup propagates as-is (503), never masked as a 401.
    private fun verifySignature(signed: SignedJWT) {
        val verifier = RSASSAVerifier(resolveSigningKey(signed).toRSAPublicKey())
        if (!signed.verify(verifier)) {
            log.warn("Apple JWT signature verification failed")
            throw InvalidJwtException()
        }
    }

    private fun resolveSigningKey(signed: SignedJWT): RSAKey {
        val kid = signed.header.keyID ?: throw InvalidJwtException()
        return jwksCache.getRsaKeyByKid(kid) ?: throw InvalidJwtException()
    }

    private fun validateIssuer(claims: JWTClaimsSet) {
        if (claims.issuer != props.issuer) {
            log.warn("Apple JWT validation failed: invalid issuer. expected={}, actual={}", props.issuer, claims.issuer)
            throw InvalidJwtException()
        }
    }

    private fun validateAudience(claims: JWTClaimsSet) {
        if (!claims.audience.contains(props.clientId)) {
            log.warn("Apple JWT validation failed: invalid audience. expected={}, actual={}", props.clientId, claims.audience)
            throw InvalidJwtException()
        }
    }

    private fun validateNotExpired(expirationTime: Date) {
        val allowedSkew = props.clockSkewSeconds * MILLIS_PER_SECOND
        if (expirationTime.time + allowedSkew < Date().time) {
            log.warn("Apple JWT validation failed: token expired. expirationTime={}", expirationTime)
            throw InvalidJwtException(message = "Token has expired")
        }
    }

    // Logs only the claim name, never the token or claim values.
    private fun <T> requireClaim(value: T?, name: String): T =
        value ?: run {
            log.warn("Apple JWT validation failed: missing claim. claim={}", name)
            throw InvalidJwtException()
        }

    private companion object {
        const val MILLIS_PER_SECOND = 1000
        const val CLAIM_EXP = "exp"
        const val CLAIM_IAT = "iat"
        const val CLAIM_SUB = "sub"
    }
}
