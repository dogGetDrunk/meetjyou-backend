package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.auth.social.socialTokenOf
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.SocialAccountNotLinkedException
import com.dogGetDrunk.meetjyou.common.exception.business.user.LastSocialAccountUnlinkException
import com.dogGetDrunk.meetjyou.common.exception.business.user.SocialAccountAlreadyLinkedException
import com.dogGetDrunk.meetjyou.common.exception.business.user.SocialProviderAlreadyLinkedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.user.dto.LinkSocialAccountRequest
import com.dogGetDrunk.meetjyou.user.dto.SocialAccountLinkResult
import com.dogGetDrunk.meetjyou.user.dto.SocialAccountResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SocialAccountService(
    private val socialVerifierRegistry: SocialVerifierRegistry,
    private val userRepository: UserRepository,
    private val currentUserProvider: CurrentUserProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun getMySocialAccounts(): List<SocialAccountResponse> =
        currentUserProvider.user.socialAccounts
            .sortedBy { it.createdAt }
            .map { SocialAccountResponse.of(it) }

    @Transactional
    fun link(request: LinkSocialAccountRequest, nonce: String?): SocialAccountLinkResult {
        val principal = socialVerifierRegistry
            .get(request.authProvider)
            .verifyAndExtract(socialTokenOf(request.idToken, request.accessToken), nonce)
        val user = currentUserProvider.user

        findOwnLinkedAccount(user, principal)?.let { existing ->
            log.info("Social account already linked, treating as retry. uuid: {}, provider: {}",
                user.uuid, existing.provider)
            return SocialAccountLinkResult(SocialAccountResponse.of(existing), created = false)
        }
        if (user.socialAccounts.any { it.provider == principal.authProvider }) {
            throw SocialProviderAlreadyLinkedException(principal.authProvider)
        }

        val account = user.addSocialAccount(principal.authProvider, principal.subject, principal.email)
        flushNewLink(principal.authProvider)

        log.info("Social account linked. uuid: {}, provider: {}", user.uuid, principal.authProvider)
        return SocialAccountLinkResult(SocialAccountResponse.of(account), created = true)
    }

    /**
     * Returns the account when the identity is already linked to [user], so a retried request
     * succeeds. An identity owned by anyone else, a withdrawn user still in the grace period
     * included, is rejected: linking must never move an identity between accounts.
     */
    private fun findOwnLinkedAccount(user: User, principal: SocialPrincipal): UserSocialAccount? {
        val owner = userRepository.findBySocialAccount(principal.authProvider, principal.subject) ?: return null
        if (owner.uuid != user.uuid) {
            throw SocialAccountAlreadyLinkedException(principal.authProvider)
        }
        return user.socialAccounts.firstOrNull {
            it.provider == principal.authProvider && it.externalId == principal.subject
        }
    }

    // Concurrent links can all pass the checks above; the two unique constraints on
    // user_social_account decide, and the request that loses gets a 409 instead of a 500.
    private fun flushNewLink(provider: AuthProvider) {
        try {
            userRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            log.info("Concurrent social account link rejected by unique constraint. provider: {}", provider, e)
            throw SocialAccountAlreadyLinkedException(provider, message = "Concurrent link hit a unique constraint")
        }
    }

    /**
     * Refuses to remove the last account, which would leave no way to sign in. The rows are read
     * with a locking query so two concurrent unlinks are serialized and the second one counts what
     * the first one left; that query ignores the transaction snapshot, so it stays correct whatever
     * was read before it.
     */
    @Transactional
    /** Returns the unlinked identity's provider subject, e.g. for revoking it with Apple. */
    fun unlink(provider: AuthProvider): String {
        val user = currentUserProvider.user
        val accounts = userRepository.findSocialAccountsForUpdate(user.id)
        val target = accounts.firstOrNull { it.provider == provider }
            ?: throw SocialAccountNotLinkedException(provider)
        if (accounts.size <= MIN_LINKED_ACCOUNTS) {
            throw LastSocialAccountUnlinkException(provider)
        }

        user.removeSocialAccount(provider)
        log.info("Social account unlinked. uuid: {}, provider: {}", user.uuid, provider)
        return target.externalId
    }

    companion object {
        private const val MIN_LINKED_ACCOUNTS = 1
    }
}
