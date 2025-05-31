package com.dogGetDrunk.meetjyou.plan.dto

import java.time.LocalDateTime

data class UpdatePlanRequest(
    val itinStart: LocalDateTime,
    val itinFinish: LocalDateTime,
    val destination: String,
    val centerLat: Double,
    val centerLng: Double,
    val memo: String?
)
