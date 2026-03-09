package com.dogGetDrunk.meetjyou.common.exception.business.terms

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException

class MissingRequiredTermsAgreementException(
    value: String,
    message: String? = null,
) : InvalidInputException(ErrorCode.MISSING_REQUIRED_TERMS_AGREEMENT, value, message)
