package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class InactiveMemberLeaveException(
    userUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_INACTIVE_MEMBER_LEAVE, userUuid.toString())
