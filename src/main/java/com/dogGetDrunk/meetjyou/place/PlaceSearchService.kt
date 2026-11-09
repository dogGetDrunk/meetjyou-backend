package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchItem
import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlaceSearchService(
    private val regionCatalog: RegionCatalog,
    private val placeSearchRepository: PlaceSearchRepository,
) {
    @Transactional(readOnly = true)
    fun search(query: String, regionCode: String?, size: Int): PlaceSearchResponse {
        val compactQuery = validateQuery(query)
        validateSize(size)
        val regionCodes = regionCode?.let { resolveRegionFilter(it) }
        val regions = regionCatalog.search(compactQuery, REGION_RESULT_LIMIT)
            .filter { regionCodes == null || it.code in regionCodes }
        val places = placeSearchRepository.search(compactQuery, regionCodes, size)
            .map { PlaceSearchItem.of(it, regionCatalog.response(it.regionCode)) }
        return PlaceSearchResponse(regions, places)
    }

    private fun validateQuery(query: String): String {
        val compact = RegionCatalog.compact(query)
        if (compact.length !in MIN_QUERY_LENGTH..MAX_QUERY_LENGTH) {
            throw InvalidInputException(
                value = query,
                message = "Search query must be $MIN_QUERY_LENGTH-$MAX_QUERY_LENGTH characters",
            )
        }
        return compact
    }

    private fun validateSize(size: Int) {
        if (size !in 1..MAX_SIZE) {
            throw InvalidInputException(value = size.toString(), message = "size must be 1-$MAX_SIZE")
        }
    }

    // A 시·도 or city filter covers every district below it, general-gu included.
    private fun resolveRegionFilter(code: String): Set<String> {
        val region = regionCatalog.find(code)
        if (region == null || !region.searchable) {
            throw InvalidInputException(value = code, message = "Unknown region code")
        }
        return regionCatalog.selfAndDescendants(region.code)
    }

    companion object {
        // ngram tokens are 2 characters: a 1-character query matches nothing.
        const val MIN_QUERY_LENGTH = 2
        const val MAX_QUERY_LENGTH = 50
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 50
        private const val REGION_RESULT_LIMIT = 10
    }
}
