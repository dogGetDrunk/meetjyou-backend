package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchItem
import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchResponse
import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchRow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlaceSearchService(
    private val regionCatalog: RegionCatalog,
    private val placeSearchRepository: PlaceSearchRepository,
    private val placeRegionStats: PlaceRegionStats,
    // Region filters with at most this many active places scan the region index; larger ones use full-text.
    private val regionScanMaxPlaces: Long = REGION_SCAN_MAX_PLACES,
) {
    @Transactional(readOnly = true)
    fun search(query: String, regionCode: String?, size: Int): PlaceSearchResponse {
        val compactQuery = validateQuery(query)
        validateSize(size)
        val regionCodes = regionCode?.let { resolveRegionFilter(it) }
        val regions = regionCatalog.search(compactQuery, REGION_RESULT_LIMIT)
            .filter { regionCodes == null || it.code in regionCodes }
        val places = searchPlaces(PlaceSearchQuery.of(compactQuery), regionCodes, size)
            .map { PlaceSearchItem.of(it, regionCatalog.response(it.regionCode)) }
        return PlaceSearchResponse(regions, places)
    }

    // Exact names first, then the rest from whichever path reads fewer rows for this region filter.
    private fun searchPlaces(query: PlaceSearchQuery, regionCodes: Set<String>?, size: Int): List<PlaceSearchRow> {
        // No indexable token: the full-text path finds nothing, so no path searches (same result everywhere).
        if (query.terms == null) return emptyList()
        val exact = placeSearchRepository.findExact(query.exact, regionCodes ?: regionCatalog.searchableCodes(), size)
        val remaining = size - exact.size
        return if (remaining > 0) exact + searchRest(query, regionCodes, remaining) else exact
    }

    private fun searchRest(query: PlaceSearchQuery, regionCodes: Set<String>?, limit: Int): List<PlaceSearchRow> =
        if (regionCodes != null && placeRegionStats.activeCount(regionCodes) <= regionScanMaxPlaces) {
            placeSearchRepository.searchRegionIndex(query, regionCodes, limit)
        } else {
            placeSearchRepository.searchFullText(query, regionCodes, limit)
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

        // A rare word scans the whole region index (about 0.3 ms per 1,000 entries measured), so this keeps
        // that under 10 ms; above it, full-text reads about size / region share rows for a common word.
        // Measured in the task spec (.pipeline/tasks/20261109-place-search-perf).
        const val REGION_SCAN_MAX_PLACES = 30_000L
    }
}
