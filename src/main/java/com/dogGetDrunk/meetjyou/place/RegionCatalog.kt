package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.place.dto.RegionResponse
import org.springframework.stereotype.Component

/**
 * In-memory view of the region table (about 300 rows). Regions change only through migrations, which
 * ship with a deploy, so one load per process is enough and responses never join the table.
 */
@Component
class RegionCatalog(
    private val regionRepository: RegionRepository,
) {
    // Loaded on first use, not at startup: a failed load is retried on the next call.
    private val snapshot: Snapshot by lazy { Snapshot(regionRepository.findAll()) }

    fun find(code: String): Region? = snapshot.byCode[code]

    fun response(code: String): RegionResponse {
        val region = find(code) ?: error("Region $code is missing from the catalog")
        return RegionResponse.of(region, sidoOf(region), cityOf(region))
    }

    /** True when [code] is [ancestorCode] itself or lies anywhere below it. */
    fun isWithin(code: String, ancestorCode: String): Boolean {
        var current = find(code)
        while (current != null) {
            if (current.code == ancestorCode) return true
            current = current.parentCode?.let { find(it) }
        }
        return false
    }

    /** [code] plus every region below it (a 시·도's districts, a city's general-gu). */
    fun selfAndDescendants(code: String): Set<String> {
        val result = mutableSetOf(code)
        var frontier = listOf(code)
        while (frontier.isNotEmpty()) {
            frontier = frontier.flatMap { snapshot.childrenOf[it].orEmpty() }.map { it.code }
            result.addAll(frontier)
        }
        return result
    }

    /** Every searchable region code: the "whole country" region filter. */
    fun searchableCodes(): Set<String> = snapshot.searchableCodes

    /** Searchable regions whose name contains [query], whitespace ignored. Exact names first, 시·도 before districts. */
    fun search(query: String, limit: Int): List<RegionResponse> {
        val needle = compact(query)
        return snapshot.searchable
            .filter { compact(it.name).contains(needle) || compact(it.fullName).contains(needle) }
            .sortedWith(compareBy({ compact(it.name) != needle }, { it.level }, { it.code }))
            .take(limit)
            .map { response(it.code) }
    }

    private fun sidoOf(region: Region): Region {
        val sidoCode = region.code.take(Region.SIDO_CODE_LENGTH)
        return find(sidoCode) ?: error("Sido $sidoCode of region ${region.code} is missing")
    }

    // A general-gu's parent is a city (SIGUNGU); every other district hangs directly under its 시·도.
    private fun cityOf(region: Region): Region? =
        region.parentCode?.let { find(it) }?.takeIf { it.level == RegionLevel.SIGUNGU }

    private class Snapshot(regions: List<Region>) {
        val byCode: Map<String, Region> = regions.associateBy { it.code }
        val childrenOf: Map<String, List<Region>> =
            regions.filter { it.parentCode != null }.groupBy { it.parentCode.orEmpty() }
        val searchable: List<Region> = regions.filter { it.searchable }
        val searchableCodes: Set<String> = searchable.mapTo(LinkedHashSet()) { it.code }
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        fun compact(value: String): String = value.replace(WHITESPACE, "")
    }
}
