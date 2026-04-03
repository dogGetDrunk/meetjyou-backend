package com.dogGetDrunk.meetjyou.common.exception.business.chat

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NotFoundException

class ChatMessageNotFoundException(
    value: String,
    message: String? = null,
) : NotFoundException(
    errorCode = ErrorCode.CHAT_MESSAGE_NOT_FOUND,
    value = value,
    message = message,
)
