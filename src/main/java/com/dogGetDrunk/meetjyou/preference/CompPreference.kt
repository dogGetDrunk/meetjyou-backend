package com.dogGetDrunk.meetjyou.preference

import com.dogGetDrunk.meetjyou.post.Post
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne

@Entity
class CompPreference(
    @ManyToOne
    var post: Post,

    @ManyToOne
    var preference: Preference,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
