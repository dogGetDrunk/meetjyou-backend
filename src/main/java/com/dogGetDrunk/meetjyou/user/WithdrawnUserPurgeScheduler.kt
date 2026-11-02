package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.UserImgService
import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class WithdrawnUserPurgeScheduler(
    private val withdrawnUserPurgeService: WithdrawnUserPurgeService,
    private val userImgService: UserImgService,
    private val discordAlertService: DiscordAlertService,
) {
    private val log = LoggerFactory.getLogger(WithdrawnUserPurgeScheduler::class.java)

    companion object {
        private const val DAILY_AT_4AM = "0 0 4 * * *"
        private const val ZONE = "Asia/Seoul"
        private const val ALERT_SOURCE = "WithdrawnUserPurgeScheduler"
        private const val ALERT_EXCEPTION = "ProfileImageDeletionFailed"
    }

    /**
     * Single app instance, so no distributed lock. Profile images go first and outside the DB
     * transaction. Users whose image deletion fails are excluded and retried the next day.
     * If the purge itself fails, deletion of already missing objects is harmless on retry.
     */
    @Scheduled(cron = DAILY_AT_4AM, zone = ZONE)
    fun purgeExpiredWithdrawals() {
        val now = Instant.now()
        val targets = withdrawnUserPurgeService.findPurgeTargets(now)
        if (targets.isEmpty()) return

        val failed = targets.filter { it.hasProfileImage && !userImgService.deleteUserProfileImg(it.uuid) }
        if (failed.isNotEmpty()) {
            val uuids = failed.joinToString(", ") { it.uuid.toString() }
            log.error("Profile image deletion failed. count={}, uuids={}", failed.size, uuids)
            discordAlertService.sendOperationalAlert(
                source = ALERT_SOURCE,
                exceptionClass = ALERT_EXCEPTION,
                summary = "Profile image deletion failed for ${failed.size} withdrawn users",
                detail = uuids,
            )
        }
        val failedIds = failed.map { it.id }.toSet()
        val purgeIds = targets.map { it.id }.filterNot { it in failedIds }
        if (purgeIds.isEmpty()) return
        val purged = withdrawnUserPurgeService.purge(purgeIds, now)
        log.info("Withdrawn user purge finished. targets={}, purged={}", targets.size, purged)
    }
}
