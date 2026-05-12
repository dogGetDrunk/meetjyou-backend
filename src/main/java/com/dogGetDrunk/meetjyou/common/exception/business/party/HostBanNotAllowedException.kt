package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import java.util.UUID

class HostBanNotAllowedException(
    targetUserUuid: UUID,
) : InvalidInputException(ErrorCode.PARTY_HOST_BAN_NOT_ALLOWED, targetUserUuid.toString())
