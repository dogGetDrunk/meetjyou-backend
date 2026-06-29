package com.dogGetDrunk.meetjyou.post.dto

import com.dogGetDrunk.meetjyou.post.PostStatus
import jakarta.validation.constraints.NotNull

data class UpdatePostStatusRequest(
    @field:NotNull(message = "status는 null일 수 없습니다.")
    val status: PostStatus,
)
