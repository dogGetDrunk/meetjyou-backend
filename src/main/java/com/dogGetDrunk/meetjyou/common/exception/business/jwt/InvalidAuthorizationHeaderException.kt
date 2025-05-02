package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class InvalidAuthorizationHeaderException(
    value: String,
    errorCode: ErrorCode = ErrorCode.INVALID_AUTHORIZATION_HEADER
) : CustomJwtException(value, errorCode)
