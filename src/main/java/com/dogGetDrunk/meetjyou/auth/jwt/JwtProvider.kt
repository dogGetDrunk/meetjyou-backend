package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.MacAlgorithm
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
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

    fun generateAccessToken(userUuid: UUID, email: String, role: Role): String {
        return generateToken(userUuid, email, role, accessTokenExpiration)
    }

    fun generateRefreshToken(userUuid: UUID, email: String): GeneratedRefreshToken {
        val jti = UUID.randomUUID()
        val now = Date()
        val expiry = Date(now.time + refreshTokenExpiration)
        val token = Jwts.builder()
            .issuer(issuer)
            .subject(email)
            .claim("userUuid", userUuid.toString())
            .id(jti.toString())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey, algorithm)
            .compact()
        return GeneratedRefreshToken(
            token = token,
            jti = jti,
            expiresAt = LocalDateTime.ofInstant(expiry.toInstant(), ZoneId.systemDefault()),
        )
    }

    private fun generateToken(userUuid: UUID, email: String, role: Role, expirationMillis: Long): String {
        val now = Date()
        val expiry = Date(now.time + expirationMillis)

        return Jwts.builder()
            .issuer(issuer)
            .subject(email)
            .claim("userUuid", userUuid.toString())
            .claim("role", role.name)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey, algorithm)
            .compact()
    }

    fun getRole(token: String): Role =
        Role.valueOf(getClaims(token)["role"]?.toString() ?: Role.USER.name)

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

    fun getJti(token: String): String =
        getClaims(token).id ?: throw InvalidJwtException(message = "Missing jti claim")

    private fun getClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
}
