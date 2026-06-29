package com.dogGetDrunk.meetjyou.post.view

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Embeddable
data class PostViewLogId(
    val userId: Long,
    val postId: Long,
) : Serializable

@Entity
@Table(name = "post_view_logs")
class PostViewLog(
    @EmbeddedId
    val id: PostViewLogId,
    var viewedAt: Instant,
)
