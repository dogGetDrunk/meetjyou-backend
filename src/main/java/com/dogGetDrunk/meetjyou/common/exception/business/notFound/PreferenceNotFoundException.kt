package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class PreferenceNotFoundException(
    val postId: String
) : NotFoundException(postId, ErrorCode.POST_NOT_FOUND) {
    constructor(postId: Long) : this(postId.toString())
}
