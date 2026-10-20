package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.util.UUID

/** What JwtAuthFilter needs from the user row: who it is and the role to grant. */
data class AuthenticatedUser(val uuid: UUID, val role: Role)

/**
 * Short-lived cache of active users for JwtAuthFilter (issue #154). The app opens a screen with a
 * burst of parallel API calls, so one load per burst removes most per-request user lookups.
 *
 * Revocation trade-off: in-app status or role changes evict the entry on commit
 * (UserAuthCacheEvictionListener), so they still apply on the next request. Changes made with SQL
 * outside the app (granting or revoking ADMIN) apply within [TTL].
 */
@Component
class AuthenticatedUserCache internal constructor(
    private val userRepository: UserRepository,
    ticker: Ticker,
) {
    @Autowired
    constructor(userRepository: UserRepository) : this(userRepository, Ticker.systemTicker())

    // expireAfterWrite, not expireAfterAccess: a client that keeps calling must not keep a
    // revoked entry alive, or the SQL-change bound above would be unlimited.
    // Nullable value type only so the loader may return null (= "do not cache"), never stored.
    private val cache: Cache<UUID, AuthenticatedUser?> = Caffeine.newBuilder()
        .expireAfterWrite(TTL)
        .maximumSize(MAX_ENTRIES)
        .ticker(ticker)
        .build()

    /**
     * Returns the user only while active. Concurrent calls for the same uuid share one DB lookup.
     * Inactive or unknown users are not cached, so they hit the DB on every call.
     */
    fun findActive(uuid: UUID): AuthenticatedUser? = cache.get(uuid) { loadActive(it) }

    // Evicts once the surrounding transaction ends. Evicting earlier, at flush, would let a
    // concurrent request re-cache the old committed row for a full TTL.
    fun evictAfterCommit(uuid: UUID) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.invalidate(uuid)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) = cache.invalidate(uuid)
        })
    }

    private fun loadActive(uuid: UUID): AuthenticatedUser? =
        userRepository.findByUuid(uuid)
            ?.takeIf { it.isActive() }
            ?.let { AuthenticatedUser(it.uuid, it.role) }

    companion object {
        // Chosen from prod iOS traffic: 91% of a user's consecutive requests are under 1s apart
        // (one screen fires many calls). 5s hits 91.6% of lookups, 30s only 94.8%, so a longer
        // TTL buys little load relief for a much longer SQL-change window.
        val TTL: Duration = Duration.ofSeconds(5)
        private const val MAX_ENTRIES = 10_000L
    }
}
