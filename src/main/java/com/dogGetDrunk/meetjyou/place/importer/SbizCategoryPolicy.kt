package com.dogGetDrunk.meetjyou.place.importer

/**
 * Which SBIZ 소분류 codes are travel destinations (decided 2026-11-06/09): every food (I2) and
 * lodging (I1) store except a few, plus seven shopping codes a traveler looks for. Codes are from
 * the 2026-09 file.
 */
object SbizCategoryPolicy {
    private const val LODGING_PREFIX = "I1"
    private const val FOOD_PREFIX = "I2"

    private val EXCLUDED = setOf(
        "I20701", // 구내식당
        "I21102", // 무도 유흥 주점
        "I10201", // 기숙사/고시원
    )

    private val SHOPPING = setOf(
        "G20405", // 편의점
        "G21802", // 기념품점
        "G21503", // 화장품 소매업
        "G21501", // 약국
        "G21801", // 예술품 소매업
        "G21301", // 서점
        "G21701", // 시계/귀금속 소매업
    )

    fun isTravelCategory(code: String): Boolean {
        if (code in EXCLUDED) return false
        return code.startsWith(FOOD_PREFIX) || code.startsWith(LODGING_PREFIX) || code in SHOPPING
    }
}
