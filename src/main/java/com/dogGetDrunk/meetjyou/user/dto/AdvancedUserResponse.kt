package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.post.PostResponse


data class AdvancedUserResponse(
    val basicUserInfo: BasicUserResponse,
    val posts: List<PostResponse>
)
