package com.dogGetDrunk.meetjyou.place.dto

import com.dogGetDrunk.meetjyou.place.tour.TourSearchStatus

/**
 * One search box, three groups: regions, stores (SBIZ, from our DB) and 관광공사 places (searched live).
 * Picking a place fills in its region on the client. [tourStatus] tells an empty tour group that found
 * nothing (OK) from one that was not searched (FAILED, DISABLED).
 */
data class PlaceSearchResponse(
    val regions: List<RegionResponse>,
    val places: List<PlaceSearchItem>,
    val tourPlaces: List<TourPlaceItem> = emptyList(),
    val tourStatus: TourSearchStatus = TourSearchStatus.DISABLED,
)

data class PlaceSearchItem(
    val id: Long,
    val name: String,
    val branchName: String?,
    val categoryName: String,
    val roadAddress: String,
    val lat: Double,
    val lng: Double,
    val region: RegionResponse,
) {
    companion object {
        fun of(row: PlaceSearchRow, region: RegionResponse): PlaceSearchItem = PlaceSearchItem(
            id = row.id,
            name = row.name,
            branchName = row.branchName,
            categoryName = row.categoryName,
            roadAddress = row.roadAddress,
            lat = row.lat,
            lng = row.lng,
            region = region,
        )
    }
}

/** A matched place as read by the search query, before its region is attached. */
data class PlaceSearchRow(
    val id: Long,
    val name: String,
    val branchName: String?,
    val categoryName: String,
    val roadAddress: String,
    val lat: Double,
    val lng: Double,
    val regionCode: String,
)
