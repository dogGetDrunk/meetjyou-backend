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
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
class Post(
    @Column(columnDefinition = "TINYINT(1) DEFAULT 1")
    var postStatus: Int = 0,

    var title: String,
    var body: String,
    var views: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now()

    @UpdateTimestamp
    val lastEditedAt: LocalDateTime = LocalDateTime.now()

    @ManyToOne
    var author: User? = null

    @ManyToOne
    var party: Party? = null

    @ManyToOne
    var plan: Plan? = null
}
