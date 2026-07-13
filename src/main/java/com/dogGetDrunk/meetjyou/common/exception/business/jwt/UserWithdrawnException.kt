package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class UserWithdrawnException(
    value: String? = null,
    message: String? = null,
) : CustomJwtException(ErrorCode.USER_WITHDRAWN, value, message)
