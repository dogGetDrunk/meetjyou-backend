package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PreferenceNotFoundException
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreference

data class UserPreferenceData(
    val gender: String,
    val age: String,
    val personalities: List<String>,
    val travelStyles: List<String>,
    val diet: List<String>,
    val etc: List<String>,
) {
    companion object {
        /**
         * Single read rule for one user's preferences. Gender, age and at least one personality are
         * required at sign-up, so a missing one on a non-purged user means broken data, not an option.
         */
        fun from(userPrefs: List<UserPreference>): UserPreferenceData {
            fun names(type: PreferenceType) = userPrefs
                .filter { it.preference.type == type }
                .map { it.preference.name }
            fun required(type: PreferenceType) = names(type)
                .ifEmpty { throw PreferenceNotFoundException(type.name) }

            return UserPreferenceData(
                gender = required(PreferenceType.GENDER).first(),
                age = required(PreferenceType.AGE).first(),
                personalities = required(PreferenceType.PERSONALITY),
                travelStyles = names(PreferenceType.TRAVEL_STYLE),
                diet = names(PreferenceType.DIET),
                etc = names(PreferenceType.ETC),
            )
        }
    }
}
