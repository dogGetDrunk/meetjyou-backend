package com.dogGetDrunk.meetjyou.terms

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
import java.time.Instant

@Entity
@Table(name = "user_terms")
class UserTerms(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_id")
    val terms: Terms,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(name = "agreed_at", insertable = false, updatable = false)
    var agreedAt: Instant? = null
        protected set
}
