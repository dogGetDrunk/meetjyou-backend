package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

open class InvalidException(
    errorCode: ErrorCode = ErrorCode.INVALID_INPUT_VALUE,
    value: String,
    message: String? = null,
) : BusinessException(value, errorCode, message)
