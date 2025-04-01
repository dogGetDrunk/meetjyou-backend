package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class PostNotFoundException(
    val postId: String
) : NotFoundException(postId, ErrorCode.POST_NOT_FOUND) {
    constructor(postId: Long) : this(postId.toString())
}
