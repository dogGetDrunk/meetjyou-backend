package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomExpiredJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
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

    fun generateAccessToken(userUuid: UUID, email: String, role: Role, expirationMillis: Long): String {
        return generateToken(userUuid, email, role, expirationMillis)
    }

    fun generateRefreshToken(userUuid: UUID, email: String): GeneratedRefreshToken {
        val jti = UUID.randomUUID()
        val now = Date()
        val expiry = Date(now.time + refreshTokenExpiration)
        val token = Jwts.builder()
            .issuer(issuer)
            .subject(email)
            .claim("userUuid", userUuid.toString())
            .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
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
            .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey, algorithm)
            .compact()
    }

    fun extractToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization") ?: return null
        return if (authHeader.startsWith("Bearer ")) authHeader.substring(7) else null
    }

    /**
     * Accepts only access tokens. Access and refresh tokens share one signing key, so the
     * signature alone cannot tell them apart; without this check a 30-day refresh token works as
     * an API credential and keeps working after logout revokes it.
     */
    fun validateAccessTokenOrThrow(token: String) {
        val claims = try {
            getClaims(token)
        } catch (e: ExpiredJwtException) {
            throw CustomExpiredJwtException(value = null, message = "Access token expired")
        } catch (e: Exception) {
            throw InvalidJwtException(message = "JWT validation failed")
        }
        if (!isAccessToken(claims)) {
            throw InvalidJwtException(message = "Not an access token")
        }
    }

    fun isRefreshToken(token: String): Boolean = try {
        isRefreshToken(getClaims(token))
    } catch (e: Exception) {
        false
    }

    fun getUsername(token: String): String = getClaims(token).subject

    fun getUserUuid(token: String): UUID = UUID.fromString(getClaims(token)["userUuid"].toString())

    fun getExpiration(token: String): Date = getClaims(token).expiration

    fun getJti(token: String): String =
        getClaims(token).id ?: throw InvalidJwtException(message = "Missing jti claim")

    // Tokens issued before the type claim existed: only refresh tokens carry a jti. The untyped
    // branches can go once every pre-claim refresh token has expired (30 days after deploy).
    private fun isAccessToken(claims: Claims): Boolean =
        when (claims[TOKEN_TYPE_CLAIM]) {
            ACCESS_TOKEN_TYPE -> true
            null -> claims.id == null
            else -> false
        }

    private fun isRefreshToken(claims: Claims): Boolean =
        when (claims[TOKEN_TYPE_CLAIM]) {
            REFRESH_TOKEN_TYPE -> true
            null -> claims.id != null
            else -> false
        }

    private fun getClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload

    companion object {
        private const val TOKEN_TYPE_CLAIM = "token_type"
        private const val ACCESS_TOKEN_TYPE = "access"
        private const val REFRESH_TOKEN_TYPE = "refresh"
    }
}
