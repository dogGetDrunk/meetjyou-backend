package com.dogGetDrunk.meetjyou.common.exception.business.plan

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException
import java.util.UUID

class PlanUpdateAccessDeniedException(
    planUuid: UUID,
    userUuid: UUID,
) : AccessDeniedException(
    ErrorCode.ACCESS_DENIED,
    planUuid.toString(),
    "User $userUuid is not allowed to modify plan $planUuid.",
)
