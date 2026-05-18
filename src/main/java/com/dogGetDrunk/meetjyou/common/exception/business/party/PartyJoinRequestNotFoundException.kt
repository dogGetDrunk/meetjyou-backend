package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class PartyJoinRequestNotFoundException(
    partyUuid: UUID,
    userUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_JOIN_REQUEST_NOT_FOUND, userUuid.toString(), "No pending join request for user $userUuid in party $partyUuid")
