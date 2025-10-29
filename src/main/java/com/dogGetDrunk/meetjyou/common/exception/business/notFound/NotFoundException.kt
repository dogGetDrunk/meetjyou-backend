package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

open class NotFoundException(
    errorCode: ErrorCode = ErrorCode.NOT_FOUND,
    value: String,
    message: String? = null,
) : BusinessException(errorCode, value, message)
