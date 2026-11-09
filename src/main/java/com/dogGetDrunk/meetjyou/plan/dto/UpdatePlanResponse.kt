package com.dogGetDrunk.meetjyou.plan.dto

import com.dogGetDrunk.meetjyou.place.dto.PlaceSummaryResponse
import com.dogGetDrunk.meetjyou.place.dto.RegionResponse
import com.dogGetDrunk.meetjyou.plan.Plan
import java.time.Instant
import java.util.UUID

data class UpdatePlanResponse(
    val uuid: UUID,
    val title: String,
    val itinStart: Instant,
    val itinFinish: Instant,
    val destination: String,
    val region: RegionResponse,
    val place: PlaceSummaryResponse?,
    val centerLat: Double,
    val centerLng: Double,
    val memo: String?,
    val favorite: Boolean,
    val userUuid: UUID,
) {
    companion object {
        fun of(plan: Plan, region: RegionResponse) = UpdatePlanResponse(
            uuid = plan.uuid,
            title = plan.title,
            itinStart = plan.itinStart,
            itinFinish = plan.itinFinish,
            destination = plan.destination,
            region = region,
            place = plan.place?.let { PlaceSummaryResponse.of(it) },
            centerLat = plan.centerLat,
            centerLng = plan.centerLng,
            memo = plan.memo,
            favorite = plan.favorite,
            userUuid = plan.owner.uuid,
        )
    }
}
