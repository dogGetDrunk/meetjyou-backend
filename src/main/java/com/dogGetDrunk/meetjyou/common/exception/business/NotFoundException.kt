package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

open class NotFoundException(
    val value: String,
    errorCode: ErrorCode = ErrorCode.NOT_FOUND
) : BusinessException(value, errorCode)
