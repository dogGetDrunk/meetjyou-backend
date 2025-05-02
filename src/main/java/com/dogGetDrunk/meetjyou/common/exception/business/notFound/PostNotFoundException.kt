package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import java.util.UUID

class PostNotFoundException(
    val uuid: String
) : NotFoundException(uuid, ErrorCode.POST_NOT_FOUND) {
//    constructor(postId: Long) : this(postId.toString())
    constructor(uuid: UUID) : this(uuid.toString())
}
