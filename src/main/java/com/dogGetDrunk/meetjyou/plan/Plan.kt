package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.time.LocalDateTime
import java.util.UUID

@Entity
class Plan(
    var itinStart: LocalDateTime,
    var itinFinish: LocalDateTime,
    var destination: String,
    var centerLat: Double,
    var centerLng: Double,

    @Column(length = 500)
    var memo: String? = null,

    @ManyToOne
    var user: User
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()
}
