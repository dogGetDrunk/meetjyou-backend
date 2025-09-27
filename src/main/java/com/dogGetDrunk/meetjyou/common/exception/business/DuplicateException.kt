package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

open class DuplicateException(
    errorCode: ErrorCode = ErrorCode.DUPLICATE,
    value: String,
    message: String? = null,
) : BusinessException(value, errorCode, message)
