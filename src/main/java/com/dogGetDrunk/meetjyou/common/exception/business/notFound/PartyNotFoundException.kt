package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import java.util.UUID

class PartyNotFoundException(
    value: UUID,
    errorCode: ErrorCode = ErrorCode.PARTY_NOT_FOUND
) : NotFoundException(value.toString(), errorCode)
