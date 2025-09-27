package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException

class TooLongBioException(
    bio: String,
    message: String? = null,
) : InvalidInputException(ErrorCode.TOO_LONG_BIO, bio, message)
