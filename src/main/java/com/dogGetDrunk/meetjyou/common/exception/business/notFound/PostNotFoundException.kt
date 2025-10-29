package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import java.util.UUID

class PostNotFoundException(
    value: String,
    message: String? = null,
) : NotFoundException(ErrorCode.POST_NOT_FOUND, value, message) {
    constructor(value: UUID, message: String? = null) : this(value.toString(), message)
}
