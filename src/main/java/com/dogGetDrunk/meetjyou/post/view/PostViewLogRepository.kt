package com.dogGetDrunk.meetjyou.post.view

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PostViewLogRepository : JpaRepository<PostViewLog, PostViewLogId> {

    @Modifying
    @Query(
        value = "INSERT INTO post_view_logs (user_id, post_id, viewed_at) VALUES (:userId, :postId, :viewedAt) ON DUPLICATE KEY UPDATE viewed_at = :viewedAt",
        nativeQuery = true,
    )
    fun upsertViewedAt(userId: Long, postId: Long, viewedAt: Instant)

    @Modifying
    @Query(
        value = "DELETE FROM post_view_logs WHERE viewed_at < :cutoff LIMIT :batchSize",
        nativeQuery = true,
    )
    fun deleteOldLogs(cutoff: Instant, batchSize: Int)
}
