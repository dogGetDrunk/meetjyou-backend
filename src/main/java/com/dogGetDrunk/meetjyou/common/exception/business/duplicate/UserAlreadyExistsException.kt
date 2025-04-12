package com.dogGetDrunk.meetjyou.common.exception.business.duplicate

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import java.util.UUID

class UserAlreadyExistsException(
    val uuid: String,
) : DuplicateException(uuid, ErrorCode.DUPLICATE_EMAIL) {
    constructor(uuid: UUID) : this(uuid.toString())
}
