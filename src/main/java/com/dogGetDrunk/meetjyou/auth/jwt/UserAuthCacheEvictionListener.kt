package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

/**
 * Keeps AuthenticatedUserCache in step with every entity-level change to a user (withdrawal,
 * restore, purge, role) without each service remembering to evict. Evicts on any update, which
 * costs at most one extra lookup. Bulk JPQL/SQL updates of the user table skip entity listeners,
 * so ConventionTest forbids them.
 *
 * Hibernate builds this through Spring's bean container while the EntityManagerFactory is being
 * created. The cache needs UserRepository, which needs that factory, so it is looked up lazily.
 * Slice tests (@DataJpaTest) have no cache bean and nothing to evict. Production always has one:
 * JwtAuthFilter can't start without it.
 */
@Component
class UserAuthCacheEvictionListener(private val authenticatedUserCache: ObjectProvider<AuthenticatedUserCache>) {

    @PostUpdate
    @PostRemove
    fun onUserChanged(user: User) {
        authenticatedUserCache.getIfAvailable()?.evictAfterCommit(user.uuid)
    }
}
