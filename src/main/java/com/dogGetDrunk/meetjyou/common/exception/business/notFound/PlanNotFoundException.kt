package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import java.util.UUID

class PlanNotFoundException(
    value: String,
    message: String? = null,
) : NotFoundException(ErrorCode.PLAN_NOT_FOUND, value, message) {
    constructor(value: UUID, message: String? = null) : this(value.toString(), message)
}
