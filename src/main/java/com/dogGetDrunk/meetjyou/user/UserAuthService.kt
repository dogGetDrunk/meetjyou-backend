package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshToken
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.IncorrectJwtSubjectException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.UserWithdrawnException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.user.UserAlreadyExistsException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.config.property.AdminProperties
import com.dogGetDrunk.meetjyou.terms.TermsService
import com.dogGetDrunk.meetjyou.user.dto.LoginRequest
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserAuthService(
    private val socialVerifierRegistry: SocialVerifierRegistry,
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
    private val termsService: TermsService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val adminProperties: AdminProperties,
    private val currentUserProvider: CurrentUserProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun registerViaSocial(request: RegistrationRequest, nonce: String? = null): TokenResponse {
        log.info("Register via social request received. email: {}, provider: {}", request.email, request.authProvider)

        val agreedTerms = termsService.validateRequiredTermsAgreement(request.agreedTermsUuids)

        val token = if (!request.idToken.isNullOrBlank()) {
            IdToken(request.idToken)
        } else {
            AccessToken(request.accessToken ?: throw InvalidJwtException(message = "No idToken or accessToken provided"))
        }

        val principal = socialVerifierRegistry
            .get(request.authProvider)
            .verifyAndExtract(token, nonce)

        if (userRepository.existsByAuthProviderAndExternalId(principal.authProvider, principal.subject)) {
            throw UserAlreadyExistsException(
                principal.email,
                message = "User already exists for provider ${request.authProvider}"
            )
        }

        val user = userService.createUser(request, principal)
        termsService.saveUserTerms(user, agreedTerms)

        log.info("User registered successfully. uuid: {}, email: {}", user.uuid, user.email)

        return issueTokenPair(user)
    }

    @Transactional
    fun loginViaSocial(request: LoginRequest, nonce: String? = null): TokenResponse {
        val token = if (!request.idToken.isNullOrBlank()) {
            IdToken(request.idToken)
        } else {
            AccessToken(request.accessToken ?: throw InvalidJwtException(message = "No idToken or accessToken provided"))
        }

        log.info("Login via social request received. token: {}, provider: {}", token.value.take(5), request.authProvider)

        val principal = socialVerifierRegistry
            .get(request.authProvider)
            .verifyAndExtract(token, nonce)

        val user = userRepository.findByAuthProviderAndExternalId(principal.authProvider, principal.subject)
            ?: throw UserNotFoundException(
                principal.email,
                message = "User not found for provider ${request.authProvider}"
            )

        if (user.status == UserStatus.DELETED) {
            throw UserWithdrawnException(user.uuid.toString(), message = "Withdrawn user attempted to log in")
        }

        log.info("User logged in successfully. uuid: {}, email: {}", user.uuid, user.email)

        return issueTokenPair(user)
    }

    @Transactional
    fun refreshToken(rawRefreshToken: String): TokenResponse {
        if (!jwtProvider.validateToken(rawRefreshToken)) {
            throw InvalidJwtException(message = "Invalid refresh token")
        }

        val jti = jwtProvider.getJti(rawRefreshToken)
        val record = refreshTokenRepository.findByJti(jti)
            ?: throw InvalidJwtException(message = "Refresh token record not found")

        if (!record.isValid) {
            throw InvalidJwtException(message = "Refresh token is revoked or expired")
        }

        val userUuid = jwtProvider.getUserUuid(rawRefreshToken)
        val email = jwtProvider.getUsername(rawRefreshToken)
        val user = userRepository.findByUuid(userUuid)
            ?: throw UserNotFoundException(userUuid, message = "User not found during token refresh")

        if (user.email != email) {
            throw IncorrectJwtSubjectException(email, message = "Email claim does not match user record")
        }

        if (user.status == UserStatus.DELETED) {
            throw UserWithdrawnException(user.uuid.toString(), message = "Withdrawn user attempted to refresh token")
        }

        record.revoke()
        log.info("Refresh token rotated. uuid: {}", user.uuid)
        return issueTokenPair(user)
    }

    @Transactional
    fun logout(rawRefreshToken: String) {
        if (!jwtProvider.validateToken(rawRefreshToken)) {
            throw InvalidJwtException(message = "Invalid refresh token")
        }

        val jti = jwtProvider.getJti(rawRefreshToken)
        val record = refreshTokenRepository.findByJti(jti)
            ?: throw InvalidJwtException(message = "Refresh token record not found")

        record.revoke()
        log.info("User logged out, refresh token revoked. jti: {}", jti)
    }

    @Transactional
    fun claimAdmin(passphrase: String): TokenResponse {
        if (passphrase != adminProperties.claimPassphrase) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid passphrase")
        }
        val user = currentUserProvider.user
        user.role = Role.ADMIN
        log.info("User promoted to ADMIN. uuid: {}", user.uuid)
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
        return TokenResponse(user.uuid, accessToken, generated.token)
    }
}
