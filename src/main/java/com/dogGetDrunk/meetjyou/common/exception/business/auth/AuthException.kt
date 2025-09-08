package com.dogGetDrunk.meetjyou.common.exception.business.auth

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

open class AuthException(
    value: String,
    errorCode: ErrorCode = ErrorCode.AUTHENTICATION_FAILED
) : BusinessException(value, errorCode)
