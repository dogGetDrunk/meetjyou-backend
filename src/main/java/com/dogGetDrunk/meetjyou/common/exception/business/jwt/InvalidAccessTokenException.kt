package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class InvalidAccessTokenException(
    value: String?,
    message: String? = null
) : CustomJwtException(ErrorCode.INVALID_ACCESS_TOKEN, value, message)
