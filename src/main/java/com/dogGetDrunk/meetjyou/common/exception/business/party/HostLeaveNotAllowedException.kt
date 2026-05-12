package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class HostLeaveNotAllowedException(
    partyUuid: UUID,
    userUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_HOST_LEAVE_NOT_ALLOWED, userUuid.toString(), "Host $userUuid cannot leave party $partyUuid")
