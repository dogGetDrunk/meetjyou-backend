package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class IncorrectJwtSubjectException(
    value: String
) : CustomJwtException(value, ErrorCode.INCORRECT_TOKEN_SUBJECT) {
    constructor(value: Long) : this(value.toString())
}
