package com.dogGetDrunk.meetjyou.post

import java.time.LocalDateTime

data class PostResponse(
    val createdAt: LocalDateTime,
    val lastEditedAt: LocalDateTime,
    val postStatus: Int,
    val title: String,
    val body: String,
    val views: Int,
)
