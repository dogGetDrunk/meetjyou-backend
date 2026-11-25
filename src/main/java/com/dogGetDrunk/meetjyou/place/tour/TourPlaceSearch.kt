package com.dogGetDrunk.meetjyou.place.tour

import com.dogGetDrunk.meetjyou.place.Region
import com.dogGetDrunk.meetjyou.place.RegionCatalog
import com.dogGetDrunk.meetjyou.place.RegionLevel
import com.dogGetDrunk.meetjyou.place.dto.TourPlaceItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

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
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(TourPlaceSearch::class.java)

    // Set when the daily quota is used up; no call until then. In memory: the app runs one instance, and
    // it holds no answer, so ADR-0004 (no caching) still holds.
    private val pausedUntil = AtomicReference<Instant?>(null)

    /** [regionCode] is already validated by the local search; it narrows the API call (see [areaOf]). */
    fun search(query: String, regionCode: String?, size: Int): TourSearchResult = when {
        !source.enabled -> TourSearchResult.DISABLED
        isPaused() -> TourSearchResult.FAILED
        else -> call(query, regionCode, size)
    }

    // Any failure, expected or not, must only empty the tour group, never fail the whole search.
    @Suppress("TooGenericExceptionCaught")
    private fun call(query: String, regionCode: String?, size: Int): TourSearchResult {
        val startedAt = System.nanoTime()
        return try {
            val area = areaOf(regionCode)
            val places = source.searchKeyword(query.trim(), area.sidoCode, area.signguCode, size)
                .mapNotNull { toItem(it, regionCode) }
            TourSearchResult(TourSearchStatus.OK, places)
        } catch (e: TourApiException) {
            if (e.reason == TourApiException.Reason.QUOTA_EXCEEDED) pause(startedAt)
            else failed(e.reason.name, e.message.orEmpty(), startedAt)
        } catch (e: RuntimeException) {
            // Class name only: client exception messages carry the request URI, key included.
            failed(UNEXPECTED, e.javaClass.simpleName, startedAt)
        }
    }

    private fun isPaused(): Boolean {
        val until = pausedUntil.get() ?: return false
        return clock.instant().isBefore(until)
    }

    // Until the next KST midnight or for an hour, whichever ends first: the quota is thought to reset at
    // midnight, but that is unconfirmed, and a later reset must not cost the whole next day.
    private fun pause(startedAt: Long): TourSearchResult {
        val now = clock.instant()
        val nextMidnight = now.atZone(KST).toLocalDate().plusDays(1).atStartOfDay(KST).toInstant()
        val resumeAt = minOf(nextMidnight, now.plus(QUOTA_PAUSE))
        pausedUntil.set(resumeAt)
        log.warn(
            "Tour search paused: reason={}, resumeAt={}, elapsedMs={}",
            TourApiException.Reason.QUOTA_EXCEEDED, resumeAt, elapsedMs(startedAt),
        )
        return TourSearchResult.FAILED
    }

    // The elapsed time is the evidence for adding a total deadline later (C-r1-02).
    private fun failed(reason: String, detail: String, startedAt: Long): TourSearchResult {
        log.warn("Tour search failed: reason={}, detail={}, elapsedMs={}", reason, detail, elapsedMs(startedAt))
        return TourSearchResult.FAILED
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / NANOS_PER_MILLI

    // The 시·군·구 goes to the API only for a plain one (right under its 시·도, no general-gu below): how the
    // API codes general-gu and their cities is unverified, and a wrong code would empty the group. Every
    // other filter sends the 시·도 alone and is narrowed afterwards by toItem.
    private fun areaOf(regionCode: String?): TourArea {
        if (regionCode == null) return TourArea(null, null)
        val sidoCode = regionCode.take(Region.SIDO_CODE_LENGTH)
        val region = regionCatalog.find(regionCode)
        val plainDistrict = region != null &&
            region.level == RegionLevel.SIGUNGU &&
            region.parentCode == sidoCode &&
            !regionCatalog.hasChildren(regionCode)
        return TourArea(sidoCode, if (plainDistrict) regionCode.drop(Region.SIDO_CODE_LENGTH) else null)
    }

    private data class TourArea(val sidoCode: String?, val signguCode: String?)

    // Dropped: a type we do not keep, no position, an unknown 시·도, or outside the region filter.
    private fun toItem(place: TourPlace, regionFilter: String?): TourPlaceItem? {
        val type = TourContentType.fromId(place.contentTypeId)
        val position = position(place)
        val region = resolveRegion(place)
            ?.takeIf { regionFilter == null || regionCatalog.isWithin(it, regionFilter) }
        if (type == null || position == null || region == null) return null
        return TourPlaceItem.of(place, type, position.first, position.second, regionCatalog.response(region))
    }

    // (lat, lng). Korea never sits at 0: a 0 means the item has no position. "NaN"/"Infinity" parse as
    // doubles, so finiteness is checked too (V-r1-02).
    private fun position(place: TourPlace): Pair<Double, Double>? {
        val lat = coordinate(place.mapy)
        val lng = coordinate(place.mapx)
        return if (lat != null && lng != null) lat to lng else null
    }

    private fun coordinate(value: String): Double? = value.toDoubleOrNull()?.takeIf { it.isFinite() && it != 0.0 }

    // 시·도 2 digits + 시·군·구 3 digits = our 5-digit 법정동 code (assumed from the Swagger, unverified live);
    // a district we do not know falls back to its 시·도, an unknown 시·도 drops the item.
    private fun resolveRegion(place: TourPlace): String? =
        listOf("${place.lDongRegnCd}${place.lDongSignguCd}", place.lDongRegnCd)
            .firstOrNull { regionCatalog.find(it)?.searchable == true }

    private companion object {
        const val UNEXPECTED = "UNEXPECTED"
        const val NANOS_PER_MILLI = 1_000_000L
        val QUOTA_PAUSE: Duration = Duration.ofHours(1)
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
