package com.dogGetDrunk.meetjyou.common.exception.business.duplicate

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class UserAlreadyExistsException(
    val email: String,
) : DuplicateException(email, ErrorCode.DUPLICATE_EMAIL)
