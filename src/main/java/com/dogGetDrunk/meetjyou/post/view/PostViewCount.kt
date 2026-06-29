package com.dogGetDrunk.meetjyou.post.view

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "post_view_counts")
class PostViewCount(
    @Id
    val postId: Long,
    var views: Long = 0,
)
