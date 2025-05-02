package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
class Post(
    @Column(columnDefinition = "TINYINT(1) DEFAULT 1")
    var isInstant: Boolean,

    var title: String,
    var content: String,
    var itinStart: LocalDate,
    var itinFinish: LocalDate,
    var location: String,
    var capacity: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now()

    @UpdateTimestamp
    val lastEditedAt: LocalDateTime = LocalDateTime.now()

    @ManyToOne
    lateinit var author: User

    @ManyToOne
    var party: Party? = null

    @ManyToOne
    var plan: Plan? = null

    @Column(columnDefinition = "TINYINT(1) DEFAULT 1")
    var postStatus: Int = 0

    var views: Int = 0
}
