package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

open class NotFoundException(
    val value: String,
    errorCode: ErrorCode = ErrorCode.NOT_FOUND
) : BusinessException(value, errorCode)
