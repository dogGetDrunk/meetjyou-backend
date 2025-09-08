package com.dogGetDrunk.meetjyou.notification.outbox

import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.user.User
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
import org.threeten.bp.LocalDateTime
import java.sql.Types
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

    val dedupKey: String? = null,

    var status: DeliveryStatus = DeliveryStatus.PENDING,

    var attempts: Int = 0,

    var availableAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now()

    enum class DeliveryStatus { PENDING, SENDING, SENT, FAILED, DEAD }
}
