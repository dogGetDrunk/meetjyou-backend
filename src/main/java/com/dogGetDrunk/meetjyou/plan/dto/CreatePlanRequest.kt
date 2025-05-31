package com.dogGetDrunk.meetjyou.plan.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.UUID

data class CreatePlanRequest(
    val itinStart: LocalDateTime,
    val itinFinish: LocalDateTime,
    val destination: String,
    val centerLat: Double,
    val centerLng: Double,
    val memo: String? = null,

    @field:JsonProperty("userUuid")
    private val userUuidString: String,
) {
    val userUuid: UUID
        get() = UUID.fromString(userUuidString)
}
