package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

open class CustomJwtException(
    errorCode: ErrorCode = ErrorCode.TOKEN_COMMON,
    value: String?,
    message: String? = null,
) : BusinessException(errorCode, value, message)
