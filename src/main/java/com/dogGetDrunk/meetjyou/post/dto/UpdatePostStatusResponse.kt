package com.dogGetDrunk.meetjyou.post.dto

import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostStatus
import java.util.UUID

data class UpdatePostStatusResponse(
    val uuid: UUID,
    val status: PostStatus,
) {
    companion object {
        fun of(post: Post) = UpdatePostStatusResponse(uuid = post.uuid, status = post.status)
    }
}
