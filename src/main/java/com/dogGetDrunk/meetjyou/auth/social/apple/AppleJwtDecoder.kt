package com.dogGetDrunk.meetjyou.auth.social.apple

import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.nimbusds.jose.crypto.RSASSAVerifier
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

    override fun decode(token: String): Jwt {
        val signed = try {
            SignedJWT.parse(token)
        } catch (e: Exception) {
            log.error("Apple JWT 파싱 실패", e)
            throw InvalidJwtException()
        }

        val claims = signed.jwtClaimsSet

        // 1. Verifying issuer
        if (claims.issuer != props.issuer) {
            log.error("Apple JWT 검증 실패: 잘못된 issuer. expected={}, actual={}", props.issuer, claims.issuer)
            throw InvalidJwtException()
        }

        // 2. Verifying audience
        if (!claims.audience.contains(props.clientId)) {
            log.error("Apple JWT 검증 실패: 잘못된 audience. expected={}, actual={}", props.clientId, claims.audience)
            throw InvalidJwtException()
        }

        // 3. Verifying expiration
        val now = Date()
        val allowedSkew = props.clockSkewSeconds * 1000
        if (claims.expirationTime.time + allowedSkew < now.time) {
            log.error("Apple JWT 검증 실패: 토큰 만료. expirationTime={}", claims.expirationTime)
            throw InvalidJwtException(message = "Token has expired")
        }

        // 4. Verifying signature
        val kid = signed.header.keyID ?: throw InvalidJwtException()
        val rsaKey = jwksCache.getRsaKeyByKid(kid) ?: throw InvalidJwtException()
        val verifier = RSASSAVerifier(rsaKey.toRSAPublicKey())
        if (!signed.verify(verifier)) {
            log.error("Apple JWT 서명 검증 실패")
            throw InvalidJwtException()
        }

        val headers = signed.header.toJSONObject()
        val jwtClaims = claims.claims
        return Jwt.withTokenValue(token)
            .headers { h -> h.putAll(headers) }
            .claims { c -> c.putAll(jwtClaims) }
            .issuedAt(claims.issueTime.toInstant())
            .expiresAt(claims.expirationTime.toInstant())
            .build()
    }
}
