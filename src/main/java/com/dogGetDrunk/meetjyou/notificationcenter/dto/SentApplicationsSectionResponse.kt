package com.dogGetDrunk.meetjyou.notificationcenter.dto

import java.time.Instant
import java.util.UUID

enum class ApplicationStatus { PENDING, ACCEPTED, REJECTED }

data class SentApplicationItem(
    val partyUuid: UUID,
    val partyName: String,
    val postUuid: UUID?,
    val status: ApplicationStatus,
    val applicationNote: String?,
    val appliedAt: Instant,
    val statusChangedAt: Instant,
    val read: Boolean,
)

data class SentApplicationsSectionResponse(
    val pendingCount: Int,
    val changedCount: Int,
    val totalCount: Long,
    val applications: List<SentApplicationItem>,
)
