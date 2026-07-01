package com.dogGetDrunk.meetjyou.party.dto

import java.time.Instant
import java.util.UUID

data class PendingJoinRequest(
    val userUuid: UUID,
    val nickname: String,
    val hasProfileImage: Boolean,
    val applicationNote: String?,
    val requestedAt: Instant,
)

data class GetPendingJoinRequestsResponse(
    val partyUuid: UUID,
    val postUuid: UUID?,
    val requests: List<PendingJoinRequest>,
)
