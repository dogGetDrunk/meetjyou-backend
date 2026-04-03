package com.dogGetDrunk.meetjyou.common.exception.business.chat

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException

class ChatRoomAccessDeniedException(
    value: String,
    message: String? = null,
) : AccessDeniedException(
    errorCode = ErrorCode.CHATROOM_ACCESS_DENIED,
    value = value,
    message = message,
)
