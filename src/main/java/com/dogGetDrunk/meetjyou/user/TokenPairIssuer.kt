package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshToken
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import org.springframework.stereotype.Component

/** Issues an access token and a stored refresh token for sign-up, login and rotation. */
@Component
class TokenPairIssuer(
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    /** Call inside the caller's transaction: revoking [rotatedFrom] is a dirty-checked update. */
    fun issue(user: User, rotatedFrom: RefreshToken? = null): TokenResponse {
        val accessToken = jwtProvider.generateAccessToken(user.uuid, user.role)
        val generated = jwtProvider.generateRefreshToken(user.uuid)
        rotatedFrom?.revoke(generated.jti.toString())
        refreshTokenRepository.save(
            RefreshToken(
                jti = generated.jti.toString(),
                user = user,
                expiresAt = generated.expiresAt,
            )
        )
        return TokenResponse(user.uuid, accessToken, generated.token)
    }
}
