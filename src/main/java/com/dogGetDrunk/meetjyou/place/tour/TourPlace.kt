package com.dogGetDrunk.meetjyou.place.tour

/** One `searchKeyword2` item as the API sent it; [mapx] is the longitude, [mapy] the latitude. */
data class TourPlace(
    val contentId: String,
    val contentTypeId: String,
    val title: String,
    val address: String,
    val mapx: String,
    val mapy: String,
    val lDongRegnCd: String,
    val lDongSignguCd: String,
)

/** Where the tour search gets its items; tests swap in a fake. */
interface TourPlaceSource {
    val enabled: Boolean

    /** [sidoCode] narrows to one 시·도 (법정동 2-digit code). Throws [TourApiException] on any failure. */
    fun searchKeyword(keyword: String, sidoCode: String?, size: Int): List<TourPlace>
}
