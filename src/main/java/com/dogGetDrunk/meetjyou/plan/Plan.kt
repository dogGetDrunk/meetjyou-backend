package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Entity
@Table(uniqueConstraints = [UniqueConstraint(name = "uk_plan_owner_client_request_id", columnNames = ["owner_id", "client_request_id"])])
class Plan(
    @Column(length = 20)
    var title: String,
    var itinStart: Instant,
    var itinFinish: Instant,
    var destination: String,
    var centerLat: Double,
    var centerLng: Double,
    var favorite: Boolean = false,

    @Column(length = 500)
    var memo: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    var owner: User,

    // Client-generated id for a single create attempt; lets a resubmit after a lost response be
    // recognized as the same request instead of creating a duplicate plan. Null for older clients
    // that don't send it yet, so no dedup is possible for them.
    @Column(name = "client_request_id")
    @JdbcTypeCode(Types.VARCHAR)
    var clientRequestId: UUID? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()

    @CreationTimestamp
    val createdAt: Instant = Instant.now()

    @UpdateTimestamp
    val updatedAt: Instant = Instant.now()
}
