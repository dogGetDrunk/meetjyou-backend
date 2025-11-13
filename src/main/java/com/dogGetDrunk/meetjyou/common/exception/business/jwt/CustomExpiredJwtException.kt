package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class CustomExpiredJwtException(
    value: String?,
    message : String? = null,
) : CustomJwtException(ErrorCode.EXPIRED_TOKEN, value, message)
