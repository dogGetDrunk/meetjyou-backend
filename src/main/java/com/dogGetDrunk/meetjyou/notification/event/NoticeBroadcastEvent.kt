package com.dogGetDrunk.meetjyou.notification.event

import java.util.UUID

data class NoticeBroadcastEvent(
    val noticeUuid: UUID,
    val noticeTitle: String,
    val noticeBody: String,
    val critical: Boolean = false,
)
