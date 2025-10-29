package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class PushTokenNotFoundException(
    value: String,
    message: String? = null,
) : NotFoundException(ErrorCode.PUSH_TOKEN_NOT_FOUND, value, message)
