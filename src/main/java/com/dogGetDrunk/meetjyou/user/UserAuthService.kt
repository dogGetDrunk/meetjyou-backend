package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshToken
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant

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
    @Value("\${jwt.rotation-overlap-seconds}") private val rotationOverlapSeconds: Long,
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

        userRepository.findByAuthProviderAndExternalId(principal.authProvider, principal.subject)?.let { existing ->
            return resolveDuplicateRegistration(existing, principal, request.authProvider)
        }

        val user = userService.createUser(request, principal)
        termsService.saveUserTerms(user, agreedTerms)

        log.info("User registered successfully. uuid: {}, email: {}", user.uuid, user.email)

        return issueTokenPair(user)
    }

    /**
     * A lost response can make the client redo the whole social-login round trip (fresh nonce,
     * fresh idToken) and resubmit registration for an account that was already created by the
     * first attempt. If that account was created just now, treat it as the same retry and log the
     * caller into it instead of failing — matching the refresh-token rotation grace window. An
     * account created outside the window is a genuine pre-existing account, not a retry.
     */
    private fun resolveDuplicateRegistration(existing: User, principal: SocialPrincipal, authProvider: AuthProvider): TokenResponse {
        if (Duration.between(existing.createdAt, Instant.now()) > Duration.ofSeconds(rotationOverlapSeconds)) {
            throw UserAlreadyExistsException(
                principal.email,
                message = "User already exists for provider $authProvider"
            )
        }
        if (existing.status == UserStatus.DELETED) {
            throw UserWithdrawnException(existing.uuid.toString(), message = "Withdrawn user attempted to register")
        }
        log.info("Registration retried within grace window after a likely lost response. uuid: {}", existing.uuid)
        return issueTokenPair(existing)
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

        val activeRecord = resolveActiveRecord(record)

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

        log.info("Refresh token rotated. uuid: {}", user.uuid)
        return issueTokenPair(user, rotatedFrom = activeRecord)
    }

    /**
     * Resolves the record to rotate from. A revoked record is still accepted when it is the
     * immediately-preceding token in the chain (its replacement has not itself been consumed yet)
     * and the rotation happened within the overlap window — this covers a client retry after a
     * lost response, matching the Auth0/Okta "rotation overlap period" pattern. Any other reuse of
     * a revoked token is treated as a breach: the entire session family is revoked.
     */
    private fun resolveActiveRecord(record: RefreshToken): RefreshToken {
        if (record.isValid) return record

        resolveGraceRetryRecord(record)?.let { return it }

        if (record.revoked) {
            log.warn("Refresh token reuse detected outside rotation grace window. uuid: {}", record.user.uuid)
            refreshTokenRepository.revokeAllByUser(record.user)
        }
        throw InvalidJwtException(message = "Refresh token is revoked or expired")
    }

    private fun resolveGraceRetryRecord(record: RefreshToken): RefreshToken? {
        if (!record.revoked) return null
        val revokedAt = record.revokedAt ?: return null
        if (Duration.between(revokedAt, Instant.now()) > Duration.ofSeconds(rotationOverlapSeconds)) return null

        val replacement = record.replacedByJti?.let { refreshTokenRepository.findByJti(it) } ?: return null
        return replacement.takeIf { it.isValid }
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

    private fun issueTokenPair(user: User, rotatedFrom: RefreshToken? = null): TokenResponse {
        val accessToken = jwtProvider.generateAccessToken(user.uuid, user.email, user.role)
        val generated = jwtProvider.generateRefreshToken(user.uuid, user.email)
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
