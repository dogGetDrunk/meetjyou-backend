package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class PartyJoinAlreadyMemberException(
    partyUuid: UUID,
    userUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_JOIN_ALREADY_MEMBER, userUuid.toString(), "User $userUuid is already a member of party $partyUuid")
