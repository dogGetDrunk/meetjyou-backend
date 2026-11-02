package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class PartyCapacityBelowJoinedException(
    partyUuid: UUID,
    capacity: Int,
    joined: Int,
) : InvalidInputException(
    ErrorCode.PARTY_CAPACITY_BELOW_JOINED,
    "partyUuid=$partyUuid, capacity=$capacity, joined=$joined",
)
