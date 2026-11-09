package com.dogGetDrunk.meetjyou.place.dto

import com.dogGetDrunk.meetjyou.place.Place

/** The place a post, party or plan points at. `active=false` means the store left the latest source file. */
data class PlaceSummaryResponse(
    val id: Long,
    val name: String,
    val branchName: String?,
    val categoryName: String,
    val roadAddress: String,
    val lat: Double,
    val lng: Double,
    val active: Boolean,
) {
    companion object {
        fun of(place: Place): PlaceSummaryResponse = PlaceSummaryResponse(
            id = place.id,
            name = place.name,
            branchName = place.branchName,
            categoryName = place.categoryName,
            roadAddress = place.roadAddress,
            lat = place.lat,
            lng = place.lng,
            active = place.active,
        )
    }
}
