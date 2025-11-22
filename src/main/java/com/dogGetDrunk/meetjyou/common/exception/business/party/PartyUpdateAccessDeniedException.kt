package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException
import java.util.UUID

class PartyUpdateAccessDeniedException(
    partyUuid: UUID,
    userUuid: UUID,
    ownerUuid: UUID? = null,
    message: String? = null,
) : AccessDeniedException(ErrorCode.ACCESS_DENIED, partyUuid.toString(), message)
