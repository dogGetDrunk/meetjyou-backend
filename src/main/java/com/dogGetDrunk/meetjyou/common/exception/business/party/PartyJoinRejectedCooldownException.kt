package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class PartyJoinRejectedCooldownException(
    partyUuid: UUID,
    userUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_JOIN_REJECTED_COOLDOWN, userUuid.toString(), "User $userUuid cannot rejoin party $partyUuid within 24 hours of rejection")
