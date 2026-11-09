package com.dogGetDrunk.meetjyou.place.dto

/** One search box, two groups: regions and places. Picking a place fills in its region on the client. */
data class PlaceSearchResponse(
    val regions: List<RegionResponse>,
    val places: List<PlaceSearchItem>,
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
