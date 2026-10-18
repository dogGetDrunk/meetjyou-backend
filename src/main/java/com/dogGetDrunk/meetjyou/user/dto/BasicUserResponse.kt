package com.dogGetDrunk.meetjyou.user.dto

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
    }
}
