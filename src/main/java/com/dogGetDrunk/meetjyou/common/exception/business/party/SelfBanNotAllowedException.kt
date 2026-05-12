package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class SelfBanNotAllowedException(
    userUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_SELF_BAN_NOT_ALLOWED, userUuid.toString())
