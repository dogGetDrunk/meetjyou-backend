package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class CustomExpiredJwtException(
    value: String
) : CustomJwtException(value, ErrorCode.EXPIRED_TOKEN)
