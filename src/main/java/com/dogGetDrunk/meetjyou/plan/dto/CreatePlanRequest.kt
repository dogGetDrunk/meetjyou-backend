package com.dogGetDrunk.meetjyou.plan.dto

import java.time.Instant

data class CreatePlanRequest(
    val itinStart: Instant,
    val itinFinish: Instant,
    val location: String,
    val centerLat: Double,
    val centerLng: Double,
    val memo: String?,
    val markers: List<CreateMarkerRequest> = emptyList(),
)
