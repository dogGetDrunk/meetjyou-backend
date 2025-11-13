package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class MissingAuthorizationHeaderException(
    value: String?,
    message: String? = null,
) : CustomJwtException(ErrorCode.MISSING_AUTHORIZATION_HEADER, value, message)
