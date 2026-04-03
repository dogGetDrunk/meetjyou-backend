package com.dogGetDrunk.meetjyou.common.exception.business.chat

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException

class ChatMessageTooLongException(
    value: String,
    message: String? = null,
) : InvalidInputException(
    errorCode = ErrorCode.CHAT_MESSAGE_TOO_LONG,
    value = value,
    message = message,
)
