package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class InactiveMemberBanException(
    targetUserUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_INACTIVE_MEMBER_BAN, targetUserUuid.toString())
