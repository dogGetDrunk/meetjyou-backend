package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class InvalidJwtException(
    // nullable value intentionally avoids revealing which part of the JWT is invalid (security by obscurity)
    value: String? = null,
    message: String? = null,
) : CustomJwtException(ErrorCode.INVALID_JWT, value, message)
