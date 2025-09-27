package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.preference.Personality

class TooManyPersonalitiesException(
    personalities: List<Personality>,
    message: String? = null,
) : InvalidInputException(
    ErrorCode.TOO_MANY_PERSONALITIES,
    personalities.toString(),
    message,
)
