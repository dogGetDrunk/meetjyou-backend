package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import java.util.UUID

class NoticeNotFoundException(
    val uuid: String
) : NotFoundException(uuid, ErrorCode.NOTICE_NOT_FOUND) {
//    constructor(noticeId: Long) : this(noticeId.toString())
    constructor(uuid: UUID) : this(uuid.toString())
}
