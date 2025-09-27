package com.dogGetDrunk.meetjyou.common.exception.business.duplicate

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException

class UserAlreadyExistsException(
    val email: String,
) : DuplicateException(ErrorCode.DUPLICATE_EMAIL, email)
