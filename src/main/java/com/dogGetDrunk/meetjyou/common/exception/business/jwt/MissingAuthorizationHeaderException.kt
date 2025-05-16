package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class MissingAuthorizationHeaderException(
    value: String?,
) : CustomJwtException(value, ErrorCode.MISSING_AUTHORIZATION_HEADER)
