package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class PartyRecruitmentClosedException(
    partyUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_RECRUITMENT_CLOSED, partyUuid.toString())
