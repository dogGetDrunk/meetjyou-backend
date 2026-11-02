package com.dogGetDrunk.meetjyou.preference

import com.dogGetDrunk.meetjyou.post.Post
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(name = CompPreference.UNIQUE_POST_PREFERENCE, columnNames = ["post_id", "preference_id"]),
    ],
)
class CompPreference(
    @ManyToOne
    var post: Post,

    @ManyToOne
    var preference: Preference,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    companion object {
        const val UNIQUE_POST_PREFERENCE = "uq_comp_preference"
    }
}
