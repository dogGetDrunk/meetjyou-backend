package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class PreferenceNotFoundException(
    preferenceName: String,

) : NotFoundException(preferenceName, ErrorCode.POST_NOT_FOUND)
