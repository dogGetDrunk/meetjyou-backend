package com.dogGetDrunk.meetjyou.notificationcenter.dto

import java.time.Instant
import java.util.UUID

data class NoticeItem(
    val uuid: UUID,
    val title: String,
    val body: String,
    val createdAt: Instant,
)

data class NoticesSectionResponse(
    val unreadCount: Int,
    val totalCount: Long,
    val notices: List<NoticeItem>,
)
