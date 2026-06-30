package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.user.User
import java.util.UUID

data class PublicUserResponse(
    val uuid: UUID,
    val nickname: String,
    val bio: String?,
    val thumbImgUrl: String?,
    val gender: String,
    val age: String,
    val personalities: List<String>,
    val travelStyles: List<String>,
    val diet: List<String>,
    val etc: List<String>,
) {
    companion object {
        fun of(user: User, prefs: UserPreferenceData, thumbImgUrl: String?): PublicUserResponse =
            PublicUserResponse(
                uuid = user.uuid,
                nickname = user.nickname,
                bio = user.bio,
                thumbImgUrl = thumbImgUrl,
                gender = prefs.gender,
                age = prefs.age,
                personalities = prefs.personalities,
                travelStyles = prefs.travelStyles,
                diet = prefs.diet,
                etc = prefs.etc,
            )
    }
}
