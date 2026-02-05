package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NotFoundException
import java.util.UUID

class PartyNotFoundException(
    value: String,
    message: String? = null,
) : NotFoundException(ErrorCode.PARTY_NOT_FOUND, value, message) {
    constructor(value: UUID, message: String? = null) : this(value.toString(), message)
}
