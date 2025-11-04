package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.common.exception.business.user.UserAlreadyExistsException
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

    @Transactional
    fun registerViaSocial(request: RegistrationRequest): TokenResponse {
        log.info("Register via social request received. email: {}, provider: {}", request.email, request.authProvider)

        val principal = socialVerifierRegistry
            .get(request.authProvider)
            .verifyAndExtract(request.credential, request.accessToken)

        if (userRepository.existsByAuthProviderAndExternalId(principal.authProvider, principal.subject)) {
            throw UserAlreadyExistsException(
                principal.email,
                message = "User already exists for provider ${request.authProvider}"
            )
        }

        val user = userService.createUser(request, principal)

        val accessToken = jwtProvider.generateAccessToken(user.uuid, user.email)
        val refreshToken = jwtProvider.generateRefreshToken(user.uuid, user.email)

        log.info("User registered successfully. uuid: {}, email: {}", user.uuid, user.email)

        return TokenResponse(user.uuid, user.email, accessToken, refreshToken)
    }

    fun loginViaSocial(request: LoginRequest): TokenResponse {
        val token = request.credential ?: request.accessToken!!

        log.info("Login via social request received. token: {}, provider: {}", token.take(5), request.authProvider)

        val principal = socialVerifierRegistry
            .get(request.authProvider)
            .verifyAndExtract(request.credential, request.accessToken)

        if (!userRepository.existsByAuthProviderAndExternalId(principal.authProvider, principal.subject)) {
            throw UserNotFoundException(
                principal.email,
                message = "User not found for provider ${request.authProvider}"
            )
        }

        val user = userRepository.findByAuthProviderAndExternalId(principal.authProvider, principal.subject)!!
        val accessToken = jwtProvider.generateAccessToken(user.uuid, user.email)
        val refreshToken = jwtProvider.generateRefreshToken(user.uuid, user.email)

        log.info("User logged in successfully. uuid: {}, email: {}", user.uuid, user.email)

        return TokenResponse(user.uuid, user.email, accessToken, refreshToken)
    }

    fun refreshToken(refreshToken: String, request: RefreshTokenRequest): TokenResponse {
        jwtProvider.validateToken(refreshToken)

        val newAccessToken = jwtProvider.generateAccessToken(request.uuid, request.email)
        val newRefreshToken = jwtProvider.generateRefreshToken(request.uuid, request.email)

        return TokenResponse(request.uuid, request.email, newAccessToken, newRefreshToken)
    }
}

