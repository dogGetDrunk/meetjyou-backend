package com.dogGetDrunk.meetjyou.place.tour

import com.dogGetDrunk.meetjyou.place.Region
import com.dogGetDrunk.meetjyou.place.RegionCatalog
import com.dogGetDrunk.meetjyou.place.dto.TourPlaceItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

data class TourSearchResult(
    val status: TourSearchStatus,
    val places: List<TourPlaceItem>,
) {
    companion object {
        val DISABLED = TourSearchResult(TourSearchStatus.DISABLED, emptyList())
        val FAILED = TourSearchResult(TourSearchStatus.FAILED, emptyList())
    }
}

/**
 * The tour group of a search. Never throws: a tour outage, quota hit or bad answer leaves the
 * regions and stores of the same search intact. Must run outside any transaction (PlaceSearchFacade).
 */
@Component
class TourPlaceSearch(
    private val source: TourPlaceSource,
    private val regionCatalog: RegionCatalog,
) {
    private val log = LoggerFactory.getLogger(TourPlaceSearch::class.java)

    /** [regionCode] is already validated by the local search; it narrows the API call to its 시·도. */
    // Any failure, expected or not, must only empty the tour group, never fail the whole search.
    @Suppress("TooGenericExceptionCaught")
    fun search(query: String, regionCode: String?, size: Int): TourSearchResult {
        if (!source.enabled) return TourSearchResult.DISABLED
        val startedAt = System.nanoTime()
        return try {
            val sidoCode = regionCode?.take(Region.SIDO_CODE_LENGTH)
            val places = source.searchKeyword(query.trim(), sidoCode, size).mapNotNull { toItem(it, regionCode) }
            TourSearchResult(TourSearchStatus.OK, places)
        } catch (e: TourApiException) {
            failed(e.reason.name, e.message.orEmpty(), startedAt)
        } catch (e: RuntimeException) {
            // Class name only: client exception messages carry the request URI, key included.
            failed(UNEXPECTED, e.javaClass.simpleName, startedAt)
        }
    }

    // The elapsed time is the evidence for adding a total deadline later (C-r1-02).
    private fun failed(reason: String, detail: String, startedAt: Long): TourSearchResult {
        val elapsedMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI
        log.warn("Tour search failed: reason={}, detail={}, elapsedMs={}", reason, detail, elapsedMs)
        return TourSearchResult.FAILED
    }

    // Dropped: a type we do not keep, no position, an unknown 시·도, or outside the region filter.
    private fun toItem(place: TourPlace, regionFilter: String?): TourPlaceItem? {
        val type = TourContentType.fromId(place.contentTypeId)
        val lat = coordinate(place.mapy)
        val lng = coordinate(place.mapx)
        val region = resolveRegion(place)
            ?.takeIf { regionFilter == null || regionCatalog.isWithin(it, regionFilter) }
        if (type == null || lat == null || lng == null || region == null) return null
        return TourPlaceItem.of(place, type, lat, lng, regionCatalog.response(region))
    }

    // Korea never sits at 0: a 0 means the item has no position.
    private fun coordinate(value: String): Double? = value.toDoubleOrNull()?.takeIf { it != 0.0 }

    // 시·도 2 digits + 시·군·구 3 digits = our 5-digit 법정동 code (assumed from the Swagger, unverified live);
    // a district we do not know falls back to its 시·도, an unknown 시·도 drops the item.
    private fun resolveRegion(place: TourPlace): String? =
        listOf("${place.lDongRegnCd}${place.lDongSignguCd}", place.lDongRegnCd)
            .firstOrNull { regionCatalog.find(it)?.searchable == true }

    private companion object {
        const val UNEXPECTED = "UNEXPECTED"
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
