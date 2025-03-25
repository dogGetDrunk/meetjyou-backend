package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class DuplicateEmailException(
    email: String
) : DuplicateException(email, ErrorCode.DUPLICATE_EMAIL)
