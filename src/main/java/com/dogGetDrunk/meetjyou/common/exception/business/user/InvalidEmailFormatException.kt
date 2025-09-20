package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException

class InvalidEmailFormatException(
    email: String,
    message: String? = null,
) : InvalidInputException(ErrorCode.INVALID_EMAIL_FORMAT, email, message)
