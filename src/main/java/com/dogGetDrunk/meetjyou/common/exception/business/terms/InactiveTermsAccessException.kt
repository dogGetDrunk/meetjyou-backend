package com.dogGetDrunk.meetjyou.common.exception.business.terms

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException

class InactiveTermsAccessException(
    value: String,
    message: String? = null,
) : InvalidInputException(ErrorCode.INACTIVE_TERMS_ACCESS, value, message)
