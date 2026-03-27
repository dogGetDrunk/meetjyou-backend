package com.dogGetDrunk.meetjyou.common.exception.business.chat

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException

class EmptyChatMessageException(
    value: String,
    message: String? = null,
) : InvalidInputException(
    errorCode = ErrorCode.EMPTY_CHAT_MESSAGE,
    value = value,
    message = message,
)
