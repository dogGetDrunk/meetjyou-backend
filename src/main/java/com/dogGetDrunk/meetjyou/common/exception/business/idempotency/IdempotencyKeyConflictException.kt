package com.dogGetDrunk.meetjyou.common.exception.business.idempotency

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException

class IdempotencyKeyConflictException(value: String) :
    DuplicateException(ErrorCode.IDEMPOTENCY_KEY_REUSED, value)
