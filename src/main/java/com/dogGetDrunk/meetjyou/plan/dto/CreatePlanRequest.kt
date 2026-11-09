package com.dogGetDrunk.meetjyou.plan.dto

import com.dogGetDrunk.meetjyou.place.Region
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreatePlanRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val title: String,
    val itinStart: Instant,
    val itinFinish: Instant,
    @field:NotBlank
    @field:Size(max = 100)
    val location: String,
    val centerLat: Double,
    val centerLng: Double,
    @field:Size(max = 500)
    val memo: String?,
    @field:Valid
    @field:Size(max = CreateMarkerRequest.MAX_MARKERS)
    val markers: List<CreateMarkerRequest> = emptyList(),
    // Optional while the app still sends only `location`: none → 미지정 region (see TravelDestinationResolver).
    @field:Size(max = Region.CODE_MAX_LENGTH)
    val regionCode: String? = null,
    val placeId: Long? = null,
) {
    // See CreatePostRequest.isItinStartAfterNow for why @JsonIgnore is required on
    // is-prefixed validator methods, not just @Schema(hidden=true).
    @AssertTrue(message = "일정 종료 시각은 일정 시작 시각 이후여야 합니다.")
    @JsonIgnore
    fun isItinFinishAfterItinStart(): Boolean = itinFinish.isAfter(itinStart)
}
