package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class IncorrectJwtSubjectException(
    value: String?,
    message: String? = null
) : CustomJwtException(ErrorCode.INCORRECT_TOKEN_SUBJECT, value, message)
