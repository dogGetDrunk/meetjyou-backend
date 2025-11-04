package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException
import com.dogGetDrunk.meetjyou.user.AuthProvider

class UserAlreadyExistsException(
    value: String,
    authProvider: AuthProvider? = null,
    message: String? = null,
) : DuplicateException(ErrorCode.DUPLICATE_USER, value, message)
