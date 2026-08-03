package com.dogGetDrunk.meetjyou.common.idempotency

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
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

// Test profile drives its schema off these JPA annotations (ddl-auto: create-drop, flyway disabled),
// so the unique constraint must be declared here too, not only in V31 - otherwise concurrency
// tests would pass against H2 without ever exercising the real constraint.
@Entity
@Table(
    name = "idempotency_key",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_idempotency_scope_user_key",
            columnNames = ["scope", "user_id", "idempotency_key"],
        )
    ],
)
class IdempotencyKey(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val scope: IdempotencyScope,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "idempotency_key", nullable = false, length = 255)
    val idempotencyKey: String,

    @Column(name = "resource_uuid", nullable = false)
    val resourceUuid: UUID,

    @Column(name = "request_hash", nullable = false, length = 64)
    val requestHash: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
