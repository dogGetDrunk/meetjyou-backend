package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException
import java.util.UUID

class PartyUpdateAccessDeniedException(
    value: String,
    message: String? = null,
) : AccessDeniedException(ErrorCode.ACCESS_DENIED, value, message) {
    constructor(partyUuid: UUID, message: String? = null) : this(partyUuid.toString(), message)
}
