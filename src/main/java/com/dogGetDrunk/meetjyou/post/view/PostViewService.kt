package com.dogGetDrunk.meetjyou.post.view

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class PostViewService(
    private val postViewCountRepository: PostViewCountRepository,
    private val postViewLogRepository: PostViewLogRepository,
) {
    private val log = LoggerFactory.getLogger(PostViewService::class.java)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun incrementIfEligible(postId: Long, userId: Long) {
        val tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES)
        val existingLog = postViewLogRepository.findById(PostViewLogId(userId, postId))

        if (existingLog.isPresent && existingLog.get().viewedAt.isAfter(tenMinutesAgo)) {
            return
        }

        postViewLogRepository.upsertViewedAt(userId, postId, Instant.now())
        postViewCountRepository.upsertIncrement(postId)
        log.debug("View count incremented. postId={}, userId={}", postId, userId)
    }

    @Transactional(readOnly = true)
    fun getViewCount(postId: Long): Long {
        return postViewCountRepository.findById(postId).map { it.views }.orElse(0L)
    }

    @Transactional(readOnly = true)
    fun getViewCounts(postIds: List<Long>): Map<Long, Long> {
        return postViewCountRepository.findAllByPostIdIn(postIds).associate { it.postId to it.views }
    }

    @Scheduled(fixedDelay = 600_000L)
    @Transactional
    fun cleanupOldViewLogs() {
        val cutoff = Instant.now().minus(10, ChronoUnit.MINUTES)
        postViewLogRepository.deleteOldLogs(cutoff, 1000)
        log.debug("Cleaned up post_view_logs older than {}", cutoff)
    }
}
