package com.dogGetDrunk.meetjyou.place.tour

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * No tour call while the daily quota is used up (data.go.kr code 22). In memory: the app runs one
 * instance, and nothing of an answer is kept, so ADR-0004 (no caching) still holds.
 */
class TourQuotaPause(private val clock: Clock) {
    private val pausedUntil = AtomicReference<Instant?>(null)

    fun isActive(): Boolean {
        val until = pausedUntil.get() ?: return false
        return clock.instant().isBefore(until)
    }

    /**
     * Pauses until the next KST midnight or for an hour, whichever ends first, and returns that time.
     * The quota is thought to reset at midnight, but that is unconfirmed: a later reset must not cost
     * the whole next day.
     */
    fun start(): Instant {
        val now = clock.instant()
        val nextMidnight = now.atZone(KST).toLocalDate().plusDays(1).atStartOfDay(KST).toInstant()
        val resumeAt = minOf(nextMidnight, now.plus(MAX_PAUSE))
        pausedUntil.set(resumeAt)
        return resumeAt
    }

    private companion object {
        val MAX_PAUSE: Duration = Duration.ofHours(1)
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
