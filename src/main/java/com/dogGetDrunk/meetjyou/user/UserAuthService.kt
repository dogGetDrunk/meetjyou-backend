package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.common.exception.business.duplicate.UserAlreadyExistsException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.user.dto.LoginRequest
import com.dogGetDrunk.meetjyou.user.dto.RefreshTokenRequest
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAuthService(
    private val socialVerifierRegistry: SocialVerifierRegistry,
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun loginViaSocial(request: LoginRequest): TokenResponse {
        val principal = socialVerifierRegistry
            .get(request.authProvider)
            .verifyAndExtract(request.credential)

        if (!userRepository.existsByAuthProviderAndExternalId(request.authProvider, request.credential)) {
            throw UserNotFoundException(
                request.credential,
                message = "User not found for provider ${request.authProvider}"
            )
        }
        
        // TODO: principal.email과 request.email이 다를 경우 어떻게 처리할지 고민 필요

        val user = userRepository.findByAuthProviderAndExternalId(request.authProvider, request.credential)!!
        val accessToken = jwtProvider.generateAccessToken(user.uuid, request.email)
        val refreshToken = jwtProvider.generateRefreshToken(user.uuid, request.email)

        return TokenResponse(user.uuid, request.email, accessToken, refreshToken)
    }

    @Transactional
    fun registerViaSocial(request: RegistrationRequest): TokenResponse {
        log.info("Register(social) request received. email: {}, provider: {}", request.email, request.authProvider)

        val principal = socialVerifierRegistry
            .get(request.authProvider)
            .verifyAndExtract(request.credential)

        if (userRepository.existsByEmail(request.email)) {
            throw UserAlreadyExistsException(request.email)
        }

        val user = userService.createUser(request, principal)

        val accessToken = jwtProvider.generateAccessToken(user.uuid, user.email)
        val refreshToken = jwtProvider.generateRefreshToken(user.uuid, user.email)

        log.info("User registered successfully. uuid: {}, email: {}", user.uuid, user.email)

        return TokenResponse(user.uuid, request.email, accessToken, refreshToken)
    }

    fun refreshToken(refreshToken: String, request: RefreshTokenRequest): TokenResponse {
        jwtProvider.validateToken(refreshToken)

        val newAccessToken = jwtProvider.generateAccessToken(request.uuid, request.email)
        val newRefreshToken = jwtProvider.generateRefreshToken(request.uuid, request.email)

        return TokenResponse(request.uuid, request.email, newAccessToken, newRefreshToken)
    }
}

