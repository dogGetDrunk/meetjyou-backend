package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class PartyJoinBannedException(
    partyUuid: UUID,
    userUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_JOIN_BANNED, userUuid.toString(), "Banned user $userUuid cannot rejoin party $partyUuid")
