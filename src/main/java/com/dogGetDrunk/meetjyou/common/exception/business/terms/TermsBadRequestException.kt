package com.dogGetDrunk.meetjyou.common.exception.business.terms

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

open class TermsBadRequestException(
    errorCode: ErrorCode,
    override val value: String? = null,
) : BusinessException(errorCode, value)
