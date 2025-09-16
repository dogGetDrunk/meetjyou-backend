package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException

class DuplicateNicknameException(
    nickname: String,
) : DuplicateException(ErrorCode.DUPLICATE_NICKNAME, nickname)
