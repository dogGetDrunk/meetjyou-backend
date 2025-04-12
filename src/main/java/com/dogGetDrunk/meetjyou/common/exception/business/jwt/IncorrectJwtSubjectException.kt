package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import java.util.UUID

class IncorrectJwtSubjectException(
    value: String
) : CustomJwtException(value, ErrorCode.INCORRECT_TOKEN_SUBJECT) {
//    constructor(value: Long) : this(value.toString())
    constructor(value: UUID) : this(value.toString())
}
