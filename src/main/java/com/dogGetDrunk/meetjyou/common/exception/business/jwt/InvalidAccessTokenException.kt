package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class InvalidAccessTokenException(
    value: String?,
) : CustomJwtException(value, ErrorCode.INVALID_ACCESS_TOKEN)
