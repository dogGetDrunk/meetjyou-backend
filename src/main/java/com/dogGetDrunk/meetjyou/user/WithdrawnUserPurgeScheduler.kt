package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.UserImgService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class WithdrawnUserPurgeScheduler(
    private val withdrawnUserPurgeService: WithdrawnUserPurgeService,
    private val userImgService: UserImgService,
) {
    private val log = LoggerFactory.getLogger(WithdrawnUserPurgeScheduler::class.java)

    companion object {
        private const val DAILY_AT_4AM = "0 0 4 * * *"
        private const val ZONE = "Asia/Seoul"
    }

    /**
     * Single app instance, so no distributed lock. Profile images go first and outside the DB
     * transaction: if the purge then fails it is retried the next day and deleting a missing
     * object is harmless, whereas the reverse order could leave an orphaned image behind.
     */
    @Scheduled(cron = DAILY_AT_4AM, zone = ZONE)
    fun purgeExpiredWithdrawals() {
        val now = Instant.now()
        val targets = withdrawnUserPurgeService.findPurgeTargets(now)
        if (targets.isEmpty()) return

        targets.filter { it.hasProfileImage }.forEach { userImgService.deleteUserProfileImg(it.uuid) }
        val purged = withdrawnUserPurgeService.purge(targets.map { it.id }, now)
        log.info("Withdrawn user purge finished. targets={}, purged={}", targets.size, purged)
    }
}
