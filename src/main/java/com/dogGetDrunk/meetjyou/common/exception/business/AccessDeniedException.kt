package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

open class AccessDeniedException(
    errorCode: ErrorCode = ErrorCode.ACCESS_DENIED,
    value: String,
    message: String? = null,
) : BusinessException(value, errorCode, message)
