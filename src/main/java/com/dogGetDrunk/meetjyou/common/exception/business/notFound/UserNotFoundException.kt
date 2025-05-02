package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import java.util.UUID

class UserNotFoundException(
    val userId: String
) : NotFoundException(userId, ErrorCode.USER_NOT_FOUND) {
//    constructor(userId: Long) : this(userId.toString())
    constructor(userId: UUID) : this(userId.toString())
}
