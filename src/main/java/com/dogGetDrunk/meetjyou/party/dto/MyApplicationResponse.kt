package com.dogGetDrunk.meetjyou.party.dto

import java.time.Instant
import java.util.UUID

enum class JoinRequestStatus { PENDING, ACCEPTED, REJECTED }

data class MyApplicationResponse(
    val partyUuid: UUID,
    val partyName: String,
    val postUuid: UUID?,
    val status: JoinRequestStatus,
    val applicationNote: String?,
    val appliedAt: Instant,
    val statusChangedAt: Instant,
)
