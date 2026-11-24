package com.dogGetDrunk.meetjyou.notification.outbox

import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Entity
class NotificationOutbox(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    @Enumerated(EnumType.STRING)
    val type: NotificationType,

    val title: String? = null,

    val body: String? = null,

    val dataJson: String,

    @Column(length = DEDUP_KEY_MAX_LENGTH)
    val dedupKey: String? = null,

    @Enumerated(EnumType.STRING)
    var status: DeliveryStatus = DeliveryStatus.PENDING,

    var attempts: Int = 0,

    var availableAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()

    @CreationTimestamp
    val createdAt: Instant = Instant.now()

    // JSON array of push_token ids that already received this notification; null = none yet.
    var deliveredTokenIds: String? = null

    enum class DeliveryStatus { PENDING, SENDING, SENT, FAILED, DEAD }

    companion object {
        // Matches V1's VARCHAR(100): H2 builds its test schema from the mapping, and without the length
        // it was 255 there, so an over-long key (#293) passed every H2 test and failed only on MySQL.
        const val DEDUP_KEY_MAX_LENGTH = 100
    }
}
