package com.dogGetDrunk.meetjyou.preference

import com.dogGetDrunk.meetjyou.post.Post
import org.springframework.data.jpa.repository.JpaRepository

interface CompPreferenceRepository : JpaRepository<CompPreference, Long> {
    fun deleteAllByPost(post: Post)
}
