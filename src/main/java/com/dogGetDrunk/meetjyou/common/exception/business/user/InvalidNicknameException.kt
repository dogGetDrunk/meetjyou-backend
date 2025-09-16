package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidException

class InvalidNicknameException(
    nickname: String,
    message: String? = null,
) : InvalidException(ErrorCode.INVALID_NICKNAME, nickname, message)
