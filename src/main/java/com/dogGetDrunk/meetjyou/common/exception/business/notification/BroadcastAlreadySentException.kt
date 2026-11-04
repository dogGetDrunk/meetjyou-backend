package com.dogGetDrunk.meetjyou.common.exception.business.notification

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

class BroadcastAlreadySentException(
    dedupPrefix: String,
) : BusinessException(ErrorCode.BROADCAST_ALREADY_SENT, dedupPrefix)
