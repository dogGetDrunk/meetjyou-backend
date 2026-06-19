package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshToken
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.user.dto.DevRegisterRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Profile("dev")
class DevUserAuthService(
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Creates a new dev user or returns a token for an existing one.
     * Uses KAKAO as a placeholder AuthProvider with a dev-prefixed externalId
     * to avoid collision with real social logins.
     */
    @Transactional
    fun registerOrLogin(request: DevRegisterRequest): TokenResponse {
        val user = userRepository.findByEmail(request.email)
            ?: userRepository.save(
                User(
                    email = request.email,
                    nickname = request.nickname,
                    authProvider = AuthProvider.KAKAO,
                    externalId = "dev-${request.email}",
                )
            )

        log.warn("Dev auth used. uuid: {}, email: {}", user.uuid, user.email)

        return issueTokenPair(user)
    }

    /**
     * Issues tokens for an existing user identified by UUID.
     * Useful when the user record already exists and only a fresh token is needed.
     */
    @Transactional
    fun getTokenForUser(uuid: UUID): TokenResponse {
        val user = userRepository.findByUuid(uuid)
            ?: throw UserNotFoundException(uuid)

        log.warn("Dev token issued for existing user. uuid: {}, email: {}", user.uuid, user.email)

        return issueTokenPair(user)
    }

    private fun issueTokenPair(user: User): TokenResponse {
        val accessToken = jwtProvider.generateAccessToken(user.uuid, user.email, user.role)
        val generated = jwtProvider.generateRefreshToken(user.uuid, user.email)
        refreshTokenRepository.save(
            RefreshToken(
                jti = generated.jti.toString(),
                user = user,
                expiresAt = generated.expiresAt,
            )
        )
        return TokenResponse(user.uuid, user.email, accessToken, generated.token)
    }
}
