package com.dogGetDrunk.meetjyou.plan.dto

import java.time.Instant

data class UpdatePlanRequest(
    val itinStart: Instant,
    val itinFinish: Instant,
    val location: String,
    val centerLat: Double,
    val centerLng: Double,
    val memo: String?,
    val favorite: Boolean,
)
