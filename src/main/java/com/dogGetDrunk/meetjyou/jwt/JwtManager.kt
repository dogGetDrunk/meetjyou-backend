package com.dogGetDrunk.meetjyou.jwt

import com.dogGetDrunk.meetjyou.common.exception.business.jwt.IncorrectJwtSubjectException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtManager(
    @Value("\${jwt.secret-key}")
    secretKey: String,

    @Value("\${jwt.issuer}")
    private val issuer: String,

    @Value("\${jwt.access-expiration}")
    private val accessTokenExpiresIn: Long,

    @Value("\${jwt.refresh-expiration}")
    private val refreshTokenExpiresIn: Long
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(secretKey.toByteArray())

    fun generateAccessToken(uuid: UUID): String {
        val now = Date()
        return Jwts.builder()
            .header()
            .type("JWT")
            .and()
            .issuer(issuer)
            .subject(uuid.toString())
            .issuedAt(now)
            .expiration(Date(now.time + accessTokenExpiresIn))
            .signWith(secretKey)
            .compact()
    }

    fun generateRefreshToken(uuid: UUID): String {
        val now = Date()
        return Jwts.builder()
            .header()
            .type("JWT")
            .and()
            .issuer(issuer)
            .subject(uuid.toString())
            .issuedAt(now)
            .expiration(Date(now.time + refreshTokenExpiresIn))
            .signWith(secretKey)
            .compact()
    }

    fun validateToken(token: String?, uuid: UUID) {
        val userIdInToken = Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject

        if (userIdInToken != uuid.toString()) {
            throw IncorrectJwtSubjectException(uuid)
        }
    }
}
