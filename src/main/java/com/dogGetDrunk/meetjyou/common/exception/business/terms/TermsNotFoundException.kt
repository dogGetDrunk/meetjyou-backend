package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class TermsNotFoundException(
    value: String,
    message: String? = null,
) : NotFoundException(ErrorCode.TERMS_NOT_FOUND, value, message)
