package com.dogGetDrunk.meetjyou.common.exception.business.terms

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException

class InvalidTermsAgreementException(
    value: String,
    message: String? = null,
) : InvalidInputException(ErrorCode.INVALID_TERMS_AGREEMENT, value, message)
