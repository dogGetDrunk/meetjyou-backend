package com.dogGetDrunk.meetjyou.party.dto

import jakarta.validation.constraints.Size

data class JoinPartyRequest(
    @field:Size(max = 500) val applicationNote: String? = null,
)
