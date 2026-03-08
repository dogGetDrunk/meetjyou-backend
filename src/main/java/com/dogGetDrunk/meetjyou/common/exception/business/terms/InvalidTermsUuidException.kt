package com.dogGetDrunk.meetjyou.common.exception.business.terms

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException

class InvalidTermsUuidException(
    value: String,
    message: String? = null,
) : InvalidInputException(ErrorCode.INVALID_TERMS_UUID, value, message)
