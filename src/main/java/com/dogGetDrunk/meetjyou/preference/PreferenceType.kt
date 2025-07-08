package com.dogGetDrunk.meetjyou.preference

enum class PreferenceType(val type: Int) {
    GENDER(0),
    AGE(1),
    PERSONALITY(2),
    TRAVEL_STYLE(3),
    DIET(4),
    ETC(5);

    companion object {
        private val typeMap = entries.associateBy { it.type }

        fun fromInt(type: Int): PreferenceType? = typeMap[type]
    }
}
