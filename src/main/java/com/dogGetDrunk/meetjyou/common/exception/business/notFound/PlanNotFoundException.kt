package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import java.util.UUID

class PlanNotFoundException(planUuid: UUID) :
    NotFoundException(planUuid.toString(), ErrorCode.PLAN_NOT_FOUND)
