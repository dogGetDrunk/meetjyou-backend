package com.dogGetDrunk.meetjyou.common.exception.business.terms

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException

class TermsContentVerificationException(
    value: String,
    message: String? = null,
) : InvalidInputException(ErrorCode.TERMS_CONTENT_VERIFICATION_FAILED, value, message)
