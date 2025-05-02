package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

open class CustomJwtException(
    val value: String,
    errorCode: ErrorCode = ErrorCode.TOKEN_COMMON
) : BusinessException(value, errorCode)
