package com.dogGetDrunk.meetjyou.place.tour

/**
 * The tour content types the search keeps. Stores (숙박, 음식점, 쇼핑) come only from SBIZ, and
 * 축제, 여행코스 are not destinations, so every other type is dropped.
 */
enum class TourContentType(val id: String, val label: String) {
    ATTRACTION("12", "관광지"),
    CULTURE("14", "문화시설"),
    LEISURE("28", "레포츠"),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: String): TourContentType? = byId[id]
    }
}
