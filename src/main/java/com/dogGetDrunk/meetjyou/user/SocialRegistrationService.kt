package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.auth.social.socialTokenOf
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.UserWithdrawnException
import com.dogGetDrunk.meetjyou.common.exception.business.user.UserAlreadyExistsException
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

@Service
class SocialRegistrationService(
    private val socialVerifierRegistry: SocialVerifierRegistry,
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val tokenPairIssuer: TokenPairIssuer,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${jwt.rotation-overlap-seconds}") private val rotationOverlapSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Not @Transactional, on purpose: the provider check is a network call that should not hold a
     * DB connection, and a sign-up that loses a unique race leaves its transaction rollback-only,
     * so finding out who won needs a second, fresh transaction.
     */
    fun register(request: RegistrationRequest, nonce: String? = null): TokenResponse {
        log.info("Register via social request received. provider: {}", request.authProvider)

        val principal = socialVerifierRegistry
            .get(request.authProvider)
            .verifyAndExtract(socialTokenOf(request.idToken, request.accessToken), nonce)

        return try {
            inTransaction { createOrResolveRetry(request, principal) }
        } catch (e: DuplicateException) {
            inTransaction { resolveLostRace(principal, e) }
        }
    }

    private fun createOrResolveRetry(request: RegistrationRequest, principal: SocialPrincipal): TokenResponse {
        userRepository.findBySocialAccount(principal.authProvider, principal.subject)?.let { existing ->
            return resolveDuplicateRegistration(existing, principal.authProvider)
        }

        val user = userService.createUser(request, principal)
        log.info("User registered successfully. uuid: {}, provider: {}", user.uuid, principal.authProvider)
        return tokenPairIssuer.issue(user)
    }

    /**
     * A losing insert waits until the winner commits, so the winner is visible here. When it holds
     * the same identity, the two requests were one double-submitted sign-up, and the loser is
     * logged in like a sequential retry. Otherwise the duplicate is real (another person took the
     * nickname) and [cause] stands. A duplicate from the sequential checks also lands here and gets
     * the same answer again.
     */
    private fun resolveLostRace(principal: SocialPrincipal, cause: DuplicateException): TokenResponse {
        val winner = userRepository.findBySocialAccount(principal.authProvider, principal.subject) ?: throw cause
        log.info("Concurrent sign-up for the same identity resolved to the existing account. uuid: {}", winner.uuid)
        return resolveDuplicateRegistration(winner, principal.authProvider)
    }

    /**
     * A lost response can make the client redo the whole social-login round trip (fresh nonce,
     * fresh idToken) and resubmit registration for an account that was already created by the
     * first attempt. If that account was created just now, treat it as the same retry and log the
     * caller into it instead of failing — matching the refresh-token rotation grace window. An
     * account created outside the window is a genuine pre-existing account, not a retry.
     */
    private fun resolveDuplicateRegistration(existing: User, authProvider: AuthProvider): TokenResponse {
        if (Duration.between(existing.createdAt, Instant.now()) > Duration.ofSeconds(rotationOverlapSeconds)) {
            throw UserAlreadyExistsException(
                authProvider.name,
                message = "User already exists for provider $authProvider"
            )
        }
        if (!existing.isActive()) {
            throw UserWithdrawnException(existing.uuid.toString(), message = "Withdrawn user attempted to register")
        }
        log.info("Registration retried within grace window after a likely lost response. uuid: {}", existing.uuid)
        return tokenPairIssuer.issue(existing)
    }

    private fun <T : Any> inTransaction(block: () -> T): T =
        transactionTemplate.execute { block() }
            ?: throw TransactionSystemException("Registration transaction returned no result")
}
