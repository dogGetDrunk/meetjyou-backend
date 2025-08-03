package com.dogGetDrunk.meetjyou.auth.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.MacAlgorithm
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtProvider(
    @Value("\${jwt.secret-key}") secret: String,
    @Value("\${jwt.access-expiration}") private val accessTokenExpiration: Long,
    @Value("\${jwt.refresh-expiration}") private val refreshTokenExpiration: Long,
    @Value("\${jwt.issuer}") private val issuer: String
) {

    private val secretKey: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))
    private val algorithm: MacAlgorithm = Jwts.SIG.HS256

    fun generateAccessToken(userUuid: UUID, email: String): String {
        return generateToken(userUuid, email, accessTokenExpiration)
    }

    fun generateRefreshToken(userUuid: UUID, email: String): String {
        return generateToken(userUuid, email, refreshTokenExpiration)
    }

    private fun generateToken(userUuid: UUID, email: String, expirationMillis: Long): String {
        val now = Date()
        val expiry = Date(now.time + expirationMillis)

        return Jwts.builder()
            .issuer(issuer)
            .subject(email)
            .claim("userUuid", userUuid.toString())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey, algorithm)
            .compact()
    }

    fun extractToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization") ?: return null
        return if (authHeader.startsWith("Bearer ")) authHeader.substring(7) else null
    }

    fun validateToken(token: String): Boolean = try {
        getClaims(token)
        true
    } catch (e: Exception) {
        false
    }

    fun getUsername(token: String): String = getClaims(token).subject

    fun getUserUuid(token: String): UUID = UUID.fromString(getClaims(token)["userUuid"].toString())

    fun getExpiration(token: String): Date = getClaims(token).expiration

    private fun getClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
}
