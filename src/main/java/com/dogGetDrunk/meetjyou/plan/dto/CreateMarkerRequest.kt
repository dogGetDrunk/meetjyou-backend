package com.dogGetDrunk.meetjyou.plan.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateMarkerRequest(
    @field:DecimalMin(MIN_LAT)
    @field:DecimalMax(MAX_LAT)
    val lat: Double,
    @field:DecimalMin(MIN_LNG)
    @field:DecimalMax(MAX_LNG)
    val lng: Double,
    val date: Instant,
    @field:Min(MIN_DAY_NUM)
    val dayNum: Int,
    @field:Min(MIN_IDX)
    val idx: Int,
    @field:NotBlank
    @field:Size(max = MAX_PLACE_LENGTH)
    val place: String,
    @field:Size(max = MAX_MEMO_LENGTH)
    val memo: String?,
) {
    companion object {
        // Markers per plan; shared by CreatePlanRequest and ReplaceMarkersRequest.
        const val MAX_MARKERS = 200
        const val MAX_PLACE_LENGTH = 100
        const val MAX_MEMO_LENGTH = 500
        const val MIN_LAT = "-90.0"
        const val MAX_LAT = "90.0"
        const val MIN_LNG = "-180.0"
        const val MAX_LNG = "180.0"
        const val MIN_DAY_NUM = 1L
        const val MIN_IDX = 0L
    }
}
