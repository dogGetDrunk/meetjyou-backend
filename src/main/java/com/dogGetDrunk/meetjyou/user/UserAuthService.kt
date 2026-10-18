package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshToken
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.refreshtoken.findChainHeadForUpdate
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.auth.social.socialTokenOf
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.IncorrectJwtSubjectException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.RefreshTokenReuseDetectedException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.UserWithdrawnException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.user.dto.LoginRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class UserAuthService(
    private val socialVerifierRegistry: SocialVerifierRegistry,
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenPairIssuer: TokenPairIssuer,
    @Value("\${jwt.rotation-overlap-seconds}") private val rotationOverlapSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun loginViaSocial(request: LoginRequest, nonce: String? = null): TokenResponse {
        val token = socialTokenOf(request.idToken, request.accessToken)

        log.info("Login via social request received. token: {}, provider: {}", token.value.take(5), request.authProvider)

        val principal = socialVerifierRegistry
            .get(request.authProvider)
            .verifyAndExtract(token, nonce)

        val user = userRepository.findBySocialAccount(principal.authProvider, principal.subject)
            ?: throw UserNotFoundException(
                request.authProvider.name,
                message = "User not found for provider ${request.authProvider}"
            )

        restoreIfWithdrawn(user)

        log.info("User logged in successfully. uuid: {}, provider: {}", user.uuid, principal.authProvider)

        return tokenPairIssuer.issue(user)
    }

    /**
     * Logging in during the withdrawal grace period cancels the withdrawal. Past it the account
     * only waits for the purge job and stays locked.
     */
    private fun restoreIfWithdrawn(user: User) {
        if (user.isActive()) return
        if (!user.isRestorable(Instant.now())) {
            throw UserWithdrawnException(
                user.uuid.toString(),
                message = "Withdrawn user attempted to log in after grace period",
            )
        }
        user.restore()
        log.info("Withdrawn user restored by logging in within grace period. uuid: {}", user.uuid)
    }

    /**
     * Every token row is read with a locking query, so concurrent requests on the same chain are
     * serialized and each one decides on what the previous one committed: two rotations of one
     * token can never both pass the validity check. A detected reuse commits its revocation even
     * though the request fails.
     */
    @Transactional(noRollbackFor = [RefreshTokenReuseDetectedException::class])
    fun refreshToken(rawRefreshToken: String): TokenResponse {
        val record = lockPresentedRecord(rawRefreshToken)
        val activeRecord = resolveActiveRecord(record)

        val userUuid = jwtProvider.getUserUuid(rawRefreshToken)
        // The signed userUuid claim must name the record's owner. Compared by uuid, not the old
        // email subject: a social account may come without an email. Once it matches, the owner
        // is the user (user_id is a NOT NULL FK and user rows are never hard-deleted).
        val user = record.user
        if (user.uuid != userUuid) {
            throw IncorrectJwtSubjectException(
                userUuid.toString(),
                message = "Refresh token record belongs to another user",
            )
        }
        if (!user.isActive()) {
            throw UserWithdrawnException(user.uuid.toString(), message = "Withdrawn user attempted to refresh token")
        }

        log.info("Refresh token rotated. uuid: {}", user.uuid)
        return tokenPairIssuer.issue(user, rotatedFrom = activeRecord)
    }

    private fun lockPresentedRecord(rawRefreshToken: String): RefreshToken {
        if (!jwtProvider.isRefreshToken(rawRefreshToken)) {
            throw InvalidJwtException(message = "Invalid refresh token")
        }
        return refreshTokenRepository.findForUpdateByJti(jwtProvider.getJti(rawRefreshToken))
            ?: throw InvalidJwtException(message = "Refresh token record not found")
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
            throw RefreshTokenReuseDetectedException(message = "Revoked refresh token reused")
        }
        throw InvalidJwtException(message = "Refresh token is revoked or expired")
    }

    // The replacement is locked too: a grace retry and a refresh of the replacement itself would
    // otherwise both rotate from it, each reading it as still valid.
    private fun resolveGraceRetryRecord(record: RefreshToken): RefreshToken? {
        if (!record.revoked) return null
        val revokedAt = record.revokedAt ?: return null
        if (Duration.between(revokedAt, Instant.now()) > Duration.ofSeconds(rotationOverlapSeconds)) return null

        val replacement = record.replacedByJti?.let { refreshTokenRepository.findForUpdateByJti(it) } ?: return null
        return replacement.takeIf { it.isValid }
    }

    /**
     * Ends the session the token belongs to, not just the presented token: if it was already
     * rotated (a refresh racing the logout, or a lost refresh response), the token that replaced
     * it would otherwise stay usable. Rows already revoked are left untouched so the chain links
     * survive for reuse detection.
     */
    @Transactional
    fun logout(rawRefreshToken: String) {
        val record = lockPresentedRecord(rawRefreshToken)
        val head = refreshTokenRepository.findChainHeadForUpdate(record)
        if (head == null) {
            log.warn("Rotation chain too long on logout, revoking all sessions. uuid: {}", record.user.uuid)
            refreshTokenRepository.revokeAllByUser(record.user)
            return
        }
        if (!head.revoked) head.revoke()
        log.info("User logged out, refresh token revoked. jti: {}", head.jti)
    }
}
