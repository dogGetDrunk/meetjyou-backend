package com.dogGetDrunk.meetjyou.notice.dto

import com.dogGetDrunk.meetjyou.notice.Notice
import java.time.LocalDateTime

data class NoticeResponse(
    val uuid: String,
    val title: String,
    val body: String,
    val createdAt: LocalDateTime,
    val lastEditedAt: LocalDateTime
) {
    companion object {
        fun from(notice: Notice): NoticeResponse {
            return NoticeResponse(
                uuid = notice.uuid.toString(),
                title = notice.title,
                body = notice.body,
                createdAt = notice.createdAt,
                lastEditedAt = notice.lastEditedAt
            )
        }
    }
}
