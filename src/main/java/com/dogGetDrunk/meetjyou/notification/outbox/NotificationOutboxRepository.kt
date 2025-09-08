package com.dogGetDrunk.meetjyou.notification.outbox

import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox.DeliveryStatus
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationOutboxRepository : JpaRepository<NotificationOutbox, Long> {
    // MySQL 8: SKIP LOCKED (다중 워커 경쟁 방지)
    @Query(
        value = """
            SELECT * FROM notification_outbox
             WHERE status = 'PENDING' AND available_at <= NOW()
             ORDER BY id
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun lockNextPendings(@Param("limit") limit: Int): List<NotificationOutbox>

    @Transactional
    @Modifying
    @Query("update NotificationOutbox o set o.status = :status where o.id in :ids")
    fun bulkUpdateStatus(@Param("ids") ids: List<Long>, @Param("status") status: DeliveryStatus)

    @Transactional
    @Modifying
    @Query(
        "update NotificationOutbox o set o.status = :status, o.attempts = :attempts, o.availableAt = :availableAt where o.id = :id"
    )
    fun updateResult(
        @Param("id") id: Long,
        @Param("status") status: DeliveryStatus,
        @Param("attempts") attempts: Int,
        @Param("availableAt") availableAt: java.time.LocalDateTime?,
    )
}
