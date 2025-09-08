package com.dogGetDrunk.meetjyou.common.exception.business.auth

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class UnauthenticatedException(
    userUuid: String,
) : AuthException(userUuid, ErrorCode.UNAUTHENTICATED)
