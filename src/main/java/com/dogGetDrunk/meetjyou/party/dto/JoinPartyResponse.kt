package com.dogGetDrunk.meetjyou.party.dto

import java.util.UUID

data class JoinPartyResponse(
    val partyUuid: UUID,
    val roomUuid: UUID?,
)
