package com.dogGetDrunk.meetjyou.post.view

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PostViewCountRepository : JpaRepository<PostViewCount, Long> {

    fun findAllByPostIdIn(postIds: Collection<Long>): List<PostViewCount>

    @Modifying
    @Query(
        value = "INSERT INTO post_view_counts (post_id, views) VALUES (:postId, 1) ON DUPLICATE KEY UPDATE views = views + 1",
        nativeQuery = true,
    )
    fun upsertIncrement(postId: Long)
}
