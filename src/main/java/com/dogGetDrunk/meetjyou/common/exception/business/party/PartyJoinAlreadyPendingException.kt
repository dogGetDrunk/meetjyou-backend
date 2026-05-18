package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class PartyJoinAlreadyPendingException(
    partyUuid: UUID,
    userUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_JOIN_ALREADY_PENDING, userUuid.toString(), "User $userUuid already has a pending join request for party $partyUuid")
