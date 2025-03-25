package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

open class DuplicateException(
    val value: String,
    errorCode: ErrorCode = ErrorCode.DUPLICATE
) : BusinessException(value, errorCode)
