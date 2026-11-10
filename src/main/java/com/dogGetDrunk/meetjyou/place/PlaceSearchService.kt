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
    // A region filter uses full-text while the words match at most this many places nationwide.
    private val fullTextMaxMatches: Long = FULL_TEXT_MAX_MATCHES,
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
        if (regionCodes != null && placeSearchRepository.countFullTextMatches(query) > fullTextMaxMatches) {
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

        // Path for a region filter. Full-text walks the matches in id order, and ids follow the import
        // file's 시·도 order, so a region late in the file is reached only after most matches elsewhere:
        // this caps those row reads (prod: 서울 '카페' read about 19k rows, 0.56 s). Past it the region index
        // stops after one page of the region's own matches; a word that is common elsewhere but rare in a
        // big region scans that region's entries instead (prod 0.35-0.57 s for 서울·경기). For a word spread
        // like the data, the region index scans about size x 1M / matches entries while full-text reads up
        // to matches rows at about 30 entries' cost each: they meet near 1,000.
        // Measured in the task spec (.pipeline/tasks/20261109-place-search-perf, "운영 실측").
        const val FULL_TEXT_MAX_MATCHES = 1_000L
    }
}
