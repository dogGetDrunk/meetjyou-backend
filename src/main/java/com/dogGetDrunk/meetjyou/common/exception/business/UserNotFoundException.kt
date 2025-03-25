package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class UserNotFoundException(
    val userId: String
) : NotFoundException(userId, ErrorCode.USER_NOT_FOUND) {
    constructor(userId: Long) : this(userId.toString())
}
