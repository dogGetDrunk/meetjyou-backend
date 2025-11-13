package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class InvalidJwtException(
    // 다른 예외와 달리 nullable value를 허용하는 이유는 JWT의 어떤 부분이 유효하지 않은지 드러내지 않기 위함
    value: String? = null,
    message: String? = null,
) : CustomJwtException(ErrorCode.INVALID_JWT, value, message)
