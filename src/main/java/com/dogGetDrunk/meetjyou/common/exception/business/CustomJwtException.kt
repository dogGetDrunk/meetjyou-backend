package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

open class CustomJwtException(
    val value: String,
    errorCode: ErrorCode = ErrorCode.TOKEN_COMMON
) : BusinessException(value, errorCode)
