package com.dogGetDrunk.meetjyou.preference

import com.dogGetDrunk.meetjyou.post.Post
import org.springframework.data.jpa.repository.JpaRepository

interface CompPreferenceRepository : JpaRepository<CompPreference, Long> {
    fun findAllByPost(post: Post): List<CompPreference>
    fun deleteAllByPost(post: Post)
}
