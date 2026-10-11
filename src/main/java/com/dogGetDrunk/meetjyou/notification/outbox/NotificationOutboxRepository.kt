package com.dogGetDrunk.meetjyou.notification.outbox

import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox.DeliveryStatus
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

// A SENDING row carries its lease expiry in available_at, so an expired lease (worker died
// mid-send) is picked up again by the same predicate as a due PENDING row.
// Public so the MySQL-backed dockerTest runs the exact statement the repository uses.
const val LOCK_NEXT_CLAIMABLE_SQL = """
    SELECT * FROM notification_outbox
     WHERE status IN ('PENDING', 'SENDING') AND available_at <= NOW()
     ORDER BY id
     LIMIT :limit
     FOR UPDATE SKIP LOCKED
"""

// The lease is computed with the database clock, the same NOW() the claim query compares
// against, so it does not depend on the JVM and MySQL session time zones agreeing.
const val MARK_CLAIMED_SQL = """
    UPDATE notification_outbox
       SET status = 'SENDING',
           attempts = attempts + 1,
           available_at = TIMESTAMPADD(SECOND, :leaseSeconds, NOW())
     WHERE id IN (:ids)
"""

interface NotificationOutboxRepository : JpaRepository<NotificationOutbox, Long> {
    // MySQL 8: SKIP LOCKED (다중 워커 경쟁 방지)
    @Query(value = LOCK_NEXT_CLAIMABLE_SQL, nativeQuery = true)
    fun lockNextClaimable(@Param("limit") limit: Int): List<NotificationOutbox>

    @Transactional
    @Modifying
    @Query(value = MARK_CLAIMED_SQL, nativeQuery = true)
    fun markClaimed(@Param("ids") ids: List<Long>, @Param("leaseSeconds") leaseSeconds: Long)

    @Transactional
    @Modifying
    @Query(
        """
        update NotificationOutbox o
           set o.status = :status, o.attempts = :attempts,
               o.availableAt = :availableAt, o.deliveredTokenIds = :deliveredTokenIds
         where o.id = :id
        """
    )
    fun updateResult(
        @Param("id") id: Long,
        @Param("status") status: DeliveryStatus,
        @Param("attempts") attempts: Int,
        @Param("availableAt") availableAt: Instant?,
        @Param("deliveredTokenIds") deliveredTokenIds: String?,
    )

    fun countByStatus(status: DeliveryStatus): Long

    fun existsByDedupKey(dedupKey: String): Boolean
}
