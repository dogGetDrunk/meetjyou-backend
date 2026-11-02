package com.dogGetDrunk.meetjyou.preference

import com.dogGetDrunk.meetjyou.post.Post
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CompPreferenceRepository : JpaRepository<CompPreference, Long> {
    fun findAllByPost(post: Post): List<CompPreference>
    fun findAllByPostIn(posts: List<Post>): List<CompPreference>

    // Bulk delete runs now. A derived delete only queues removals until flush, while the IDENTITY
    // inserts that follow run at once, so re-saving a kept preference hit uq_comp_preference.
    @Modifying
    @Query("DELETE FROM CompPreference c WHERE c.post = :post")
    fun deleteAllByPost(post: Post)
}
