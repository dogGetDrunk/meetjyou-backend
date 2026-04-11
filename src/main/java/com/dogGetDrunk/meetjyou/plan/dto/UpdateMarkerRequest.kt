package com.dogGetDrunk.meetjyou.plan.dto

import java.time.Instant

data class UpdateMarkerRequest(
    val lat: Double,
    val lng: Double,
    val date: Instant,
    val dayNum: Int,
    val idx: Int,
    val place: String,
    val memo: String?,
)
