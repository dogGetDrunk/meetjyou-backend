package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class PushTokenNotFoundException(
    token: String,
) : NotFoundException(token, ErrorCode.PUSH_TOKEN_NOT_FOUND)
