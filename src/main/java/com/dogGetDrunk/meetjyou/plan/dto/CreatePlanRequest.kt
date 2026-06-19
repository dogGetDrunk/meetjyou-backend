package com.dogGetDrunk.meetjyou.plan.dto

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreatePlanRequest(
    val itinStart: Instant,
    val itinFinish: Instant,
    @field:NotBlank
    @field:Size(max = 100)
    val location: String,
    val centerLat: Double,
    val centerLng: Double,
    @field:Size(max = 500)
    val memo: String?,
    val markers: List<CreateMarkerRequest> = emptyList(),
) {
    @AssertTrue(message = "일정 종료 시각은 일정 시작 시각 이후여야 합니다.")
    fun isItinFinishAfterItinStart(): Boolean = itinFinish.isAfter(itinStart)
}
