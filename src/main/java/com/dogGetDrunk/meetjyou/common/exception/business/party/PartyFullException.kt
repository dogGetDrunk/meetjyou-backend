package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class PartyFullException(
    partyUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_FULL, partyUuid.toString())
