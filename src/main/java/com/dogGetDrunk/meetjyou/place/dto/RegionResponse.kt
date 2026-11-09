package com.dogGetDrunk.meetjyou.place.dto

import com.dogGetDrunk.meetjyou.place.Region
import com.dogGetDrunk.meetjyou.place.RegionLevel

/**
 * A region with its 시·도 and, for a general-gu (일반구, e.g. 수원시 장안구), its city — so clients never
 * parse names to find the parent.
 */
data class RegionResponse(
    val code: String,
    val name: String,
    val fullName: String,
    val level: RegionLevel,
    val sidoCode: String,
    val sidoName: String,
    val cityCode: String?,
    val cityName: String?,
) {
    companion object {
        fun of(region: Region, sido: Region, city: Region?): RegionResponse = RegionResponse(
            code = region.code,
            name = region.name,
            fullName = region.fullName,
            level = region.level,
            sidoCode = sido.code,
            sidoName = sido.name,
            cityCode = city?.code,
            cityName = city?.name,
        )
    }
}
