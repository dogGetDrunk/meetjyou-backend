package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class ChatRoomNotFoundException(
    roomUuid: String,
    errorCode: ErrorCode = ErrorCode.CHAT_ROOM_NOT_FOUND
) : NotFoundException(roomUuid, errorCode)
