package com.dogGetDrunk.meetjyou.notificationcenter.dto

import java.time.Instant
import java.util.UUID

data class ReceivedApplicationItem(
    val userUuid: UUID,
    val nickname: String,
    val thumbImgUrl: String?,
    val partyUuid: UUID,
    val partyName: String,
    val postUuid: UUID?,
    val applicationNote: String?,
    val requestedAt: Instant,
    val read: Boolean,
)

data class ReceivedApplicationsSectionResponse(
    val unreadCount: Int,
    val applications: List<ReceivedApplicationItem>,
)
