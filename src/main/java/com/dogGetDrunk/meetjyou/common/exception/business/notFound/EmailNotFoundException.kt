package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class EmailNotFoundException(
    email: String,
    message: String? = null,
) : NotFoundException(ErrorCode.EMAIL_NOT_FOUND, email, message)
