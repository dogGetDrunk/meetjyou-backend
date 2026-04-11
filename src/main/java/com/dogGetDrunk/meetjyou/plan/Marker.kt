package com.dogGetDrunk.meetjyou.plan

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Entity
class Marker(
    var lat: Double,
    var lng: Double,
    var date: Instant,
    var dayNum: Int,
    var idx: Int,

    @Column(length = 100)
    var place: String,

    @Column(length = 500)
    var memo: String? = null,

    @ManyToOne
    @JoinColumn(name = "plan_id")
    var plan: Plan,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()
}
