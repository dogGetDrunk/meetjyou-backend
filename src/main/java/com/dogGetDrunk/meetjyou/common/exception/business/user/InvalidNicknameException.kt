package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException

class InvalidNicknameException(
    nickname: String,
    message: String? = null,
) : InvalidInputException(ErrorCode.INVALID_NICKNAME, nickname, message)
