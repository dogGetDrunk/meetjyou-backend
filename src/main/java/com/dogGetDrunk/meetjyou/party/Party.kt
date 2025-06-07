package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.plan.Plan
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import java.sql.Types
import java.time.LocalDateTime
import java.util.UUID

@Entity
class Party(
    var itinStart: LocalDateTime,
    var itinFinish: LocalDateTime,
    var destination: String,
    var joined: Int,
    var max: Int,
    var name: String,
    var imgUrl: String,
    var thumbImgUrl: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()

    @CreationTimestamp
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    var lastEditedAt: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    var plan: Plan? = null
}
