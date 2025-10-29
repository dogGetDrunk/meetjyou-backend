package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class PreferenceNotFoundException(
    value: String,
    message: String? = null,
) : NotFoundException(ErrorCode.PREFERENCE_NOT_FOUND, value, message)
