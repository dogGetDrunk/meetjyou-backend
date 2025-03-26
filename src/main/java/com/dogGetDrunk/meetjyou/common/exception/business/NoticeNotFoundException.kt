package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class NoticeNotFoundException(
    val noticeId: String
) : NotFoundException(noticeId, ErrorCode.NOTICE_NOT_FOUND) {
    constructor(noticeId: Long) : this(noticeId.toString())
}
