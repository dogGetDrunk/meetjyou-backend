package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyRepository
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Final step of withdrawal: once the grace period is over the account can no longer be restored,
 * so its personal data is stripped and its social accounts are released for a fresh sign-up.
 */
@Service
class WithdrawnUserPurgeService(
    private val userRepository: UserRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val userSessionRevoker: UserSessionRevoker,
    private val notificationPreferenceRepository: UserNotificationPreferenceRepository,
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
) {
    private val log = LoggerFactory.getLogger(WithdrawnUserPurgeService::class.java)

    @Transactional(readOnly = true)
    fun findPurgeTargets(now: Instant): List<User> {
        val cutoff = now.minus(User.WITHDRAWAL_GRACE_PERIOD)
        return userRepository.findAllByStatusAndWithdrawnAtLessThanEqual(UserStatus.DELETED, cutoff)
    }

    /** Re-checks each target inside the transaction instead of trusting the earlier read. */
    @Transactional
    fun purge(userIds: Collection<Long>, now: Instant): Int {
        val targets = userRepository.findAllById(userIds)
            .filter { it.status == UserStatus.DELETED && !it.isRestorable(now) }
        if (targets.isEmpty()) return 0

        val targetIds = targets.map { it.id }
        userRepository.deleteSocialAccountsByUserIdIn(targetIds)
        userPreferenceRepository.deleteAllByUserIdIn(targetIds)
        userSessionRevoker.deleteAll(targetIds)
        // Per-user settings and request keys. Kept on purpose: terms agreements (consent evidence)
        // and post view logs (service usage records), both retained under the withdrawal policy.
        notificationPreferenceRepository.deleteAllByUserIdIn(targetIds)
        idempotencyKeyRepository.deleteAllByUserIdIn(targetIds)
        targets.forEach { it.anonymize() }

        log.info("Purged withdrawn users. count={}, uuids={}", targets.size, targets.map { it.uuid })
        return targets.size
    }
}
