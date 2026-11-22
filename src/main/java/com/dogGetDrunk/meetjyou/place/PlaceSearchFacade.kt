package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchResponse
import com.dogGetDrunk.meetjyou.place.tour.TourPlaceSearch
import org.springframework.stereotype.Service

/**
 * The search box: regions and stores from our DB, then 관광공사 places live. Deliberately not
 * transactional: the tour call waits up to its timeouts, and holding a DB connection through that wait
 * would starve the pool. The DB part keeps its own read-only transaction in [PlaceSearchService].
 */
@Service
class PlaceSearchFacade(
    private val placeSearchService: PlaceSearchService,
    private val tourPlaceSearch: TourPlaceSearch,
) {
    // Local search first: it validates the input, so a rejected request never reaches the tour API.
    fun search(query: String, regionCode: String?, size: Int): PlaceSearchResponse {
        val local = placeSearchService.search(query, regionCode, size)
        val tour = tourPlaceSearch.search(query, regionCode, size)
        return local.copy(tourPlaces = tour.places, tourStatus = tour.status)
    }
}
