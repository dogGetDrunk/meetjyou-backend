package com.dogGetDrunk.meetjyou.post.dto

import java.time.LocalDateTime

data class GetPostResponse(
    val createdAt: LocalDateTime,
    val lastEditedAt: LocalDateTime,
    val postStatus: Int,
    val title: String,
    val content: String,
    val views: Int,
    val authorId: Long,
)
