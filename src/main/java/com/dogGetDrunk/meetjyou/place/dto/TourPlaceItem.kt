package com.dogGetDrunk.meetjyou.place.dto

import com.dogGetDrunk.meetjyou.place.tour.TourContentType
import com.dogGetDrunk.meetjyou.place.tour.TourPlace

/**
 * A 관광공사 place found live. Not stored anywhere (ADR-0004); images are left out of search DTOs.
 * Clients must show the source as "출처 : ⓒ한국관광콘텐츠랩" or "ⓒ한국관광공사" (never "TourAPI").
 */
data class TourPlaceItem(
    val contentId: String,
    val name: String,
    val categoryName: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val region: RegionResponse,
) {
    companion object {
        fun of(
            place: TourPlace,
            type: TourContentType,
            lat: Double,
            lng: Double,
            region: RegionResponse,
        ): TourPlaceItem = TourPlaceItem(
            contentId = place.contentId,
            name = place.title,
            categoryName = type.label,
            address = place.address,
            lat = lat,
            lng = lng,
            region = region,
        )
    }
}
