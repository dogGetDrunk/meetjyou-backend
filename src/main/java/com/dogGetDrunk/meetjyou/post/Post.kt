package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.FetchType
import jakarta.persistence.Id
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
@Table(uniqueConstraints = [UniqueConstraint(name = "uk_post_author_client_request_id", columnNames = ["author_id", "client_request_id"])])
class Post(
    party: Party,
    @Column(columnDefinition = "TINYINT(1) DEFAULT 1")
    var isInstant: Boolean,
    var title: String,
    var content: String,
    var itinStart: Instant,
    var itinFinish: Instant,
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
    val createdAt: Instant = Instant.now()
    @UpdateTimestamp
    val lastEditedAt: Instant = Instant.now()
    @ManyToOne(fetch = FetchType.LAZY)
    lateinit var author: User
    @ManyToOne(fetch = FetchType.LAZY)
    var party: Party = party
    @ManyToOne(fetch = FetchType.LAZY)
    var plan: Plan? = null
    @Column(columnDefinition = "TINYINT(1) DEFAULT 1")
    var isPlanPublic: Boolean? = null
    @Enumerated(EnumType.STRING)
    var status: PostStatus = PostStatus.RECRUITING
    var joined: Int = 1

    // Client-generated id for a single create attempt; lets a resubmit after a lost response be
    // recognized as the same request instead of creating a duplicate party/chat room/post. Null
    // for older clients that don't send it yet, so no dedup is possible for them.
    @Column(name = "client_request_id")
    @JdbcTypeCode(Types.VARCHAR)
    var clientRequestId: UUID? = null

    fun completeRecruitment() {
        status = PostStatus.RECRUITMENT_COMPLETED
    }
}
