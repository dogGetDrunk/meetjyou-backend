package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class EmailNotFoundException(
    email: String
) : NotFoundException(email, ErrorCode.EMAIL_NOT_FOUND)
