package com.dogGetDrunk.meetjyou.plan.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Size

data class ReplaceMarkersRequest(
    @field:Valid
    @field:Size(max = CreateMarkerRequest.MAX_MARKERS)
    val markers: List<CreateMarkerRequest>,
)
