package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class VersionNotFoundException(
    value: String,
    message: String? = null,
) : NotFoundException(ErrorCode.VERSION_NOT_FOUND, value, message)
