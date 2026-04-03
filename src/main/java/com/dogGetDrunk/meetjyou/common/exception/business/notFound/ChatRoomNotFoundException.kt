package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class ChatRoomNotFoundException(
    value: String,
    message: String? = null,
) : NotFoundException(ErrorCode.CHATROOM_NOT_FOUND, value, message)
