package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.preference.TravelStyle

class TooManyTravelStylesException(
    travelStyles: List<TravelStyle>,
    message: String? = null,
) : InvalidInputException(
    ErrorCode.TOO_MANY_TRAVEL_STYLES,
    travelStyles.toString(),
    message,
)
