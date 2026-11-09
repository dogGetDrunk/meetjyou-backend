package com.dogGetDrunk.meetjyou.plan.dto

import com.dogGetDrunk.meetjyou.place.dto.PlaceSummaryResponse
import com.dogGetDrunk.meetjyou.place.dto.RegionResponse
import com.dogGetDrunk.meetjyou.plan.Marker
import com.dogGetDrunk.meetjyou.plan.Plan
import java.time.Instant
import java.util.UUID

data class GetPlanResponse(
    val uuid: UUID,
    val title: String,
    val itinStart: Instant,
    val itinFinish: Instant,
    val destination: String,
    val centerLat: Double,
    val centerLng: Double,
    val memo: String?,
    val favorite: Boolean,
    val userUuid: UUID,
    val markers: List<MarkerResponse>,
    // Nullable with defaults: party.planSnapshot stores this as JSON, and snapshots written before
    // travel destinations existed have neither field.
    val region: RegionResponse? = null,
    val place: PlaceSummaryResponse? = null,
) {
    companion object {
        fun of(plan: Plan, markers: List<Marker>, region: RegionResponse?) = GetPlanResponse(
            uuid = plan.uuid,
            title = plan.title,
            itinStart = plan.itinStart,
            itinFinish = plan.itinFinish,
            destination = plan.destination,
            centerLat = plan.centerLat,
            centerLng = plan.centerLng,
            memo = plan.memo,
            favorite = plan.favorite,
            userUuid = plan.owner.uuid,
            markers = markers.map { MarkerResponse.of(it) },
            region = region,
            place = plan.place?.let { PlaceSummaryResponse.of(it) },
        )
    }
}
