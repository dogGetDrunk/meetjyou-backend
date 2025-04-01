package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.post.dto.GetPostResponse
import org.springframework.data.domain.Page

data class AdvancedUserResponse(
    val basicUserInfo: BasicUserResponse,
    val posts: Page<GetPostResponse>
)
