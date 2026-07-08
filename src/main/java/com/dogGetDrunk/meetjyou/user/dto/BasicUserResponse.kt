package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PreferenceNotFoundException
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import java.util.UUID

data class BasicUserResponse(
    val uuid: UUID,
    val nickname: String,
    val bio: String?,
    val hasProfileImage: Boolean,
    val gender: String,
    val age: String,
    val personalities: List<String>,
    val travelStyles: List<String>,
    val diet: List<String>,
    val etc: List<String>,
    val authProvider: AuthProvider,
    val marketingSnsConsented: Boolean,
    val marketingEmailConsented: Boolean,
) {
    companion object {
        fun of(user: User, prefs: UserPreferenceData): BasicUserResponse =
            BasicUserResponse(
                uuid = user.uuid,
                nickname = user.nickname,
                bio = user.bio,
                hasProfileImage = user.hasProfileImage,
                gender = prefs.gender,
                age = prefs.age,
                personalities = prefs.personalities,
                travelStyles = prefs.travelStyles,
                diet = prefs.diet,
                etc = prefs.etc,
                authProvider = user.authProvider,
                marketingSnsConsented = user.marketingSnsConsented,
                marketingEmailConsented = user.marketingEmailConsented,
            )

        fun of(user: User, userPrefs: List<UserPreference>): BasicUserResponse {
            fun first(type: PreferenceType) = userPrefs
                .firstOrNull { it.preference.type == type }?.preference?.name
                ?: throw PreferenceNotFoundException(type.name)
            fun nameList(type: PreferenceType) = userPrefs
                .filter { it.preference.type == type }.map { it.preference.name }
            return BasicUserResponse(
                uuid = user.uuid,
                nickname = user.nickname,
                bio = user.bio,
                hasProfileImage = user.hasProfileImage,
                gender = first(PreferenceType.GENDER),
                age = first(PreferenceType.AGE),
                personalities = nameList(PreferenceType.PERSONALITY),
                travelStyles = nameList(PreferenceType.TRAVEL_STYLE),
                diet = nameList(PreferenceType.DIET),
                etc = nameList(PreferenceType.ETC),
                authProvider = user.authProvider,
                marketingSnsConsented = user.marketingSnsConsented,
                marketingEmailConsented = user.marketingEmailConsented,
            )
        }
    }
}
