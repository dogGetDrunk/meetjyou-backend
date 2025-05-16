package com.dogGetDrunk.meetjyou.common.exception.business.duplicate

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

open class DuplicateException(
    value: String,
    errorCode: ErrorCode = ErrorCode.DUPLICATE
) : BusinessException(value, errorCode)
