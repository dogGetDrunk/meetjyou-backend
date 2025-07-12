package com.dogGetDrunk.meetjyou.post.dto

import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.preference.CompPreference
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import java.time.LocalDate
import java.time.LocalDateTime

data class CreatePostResponse(
    val uuid: String,
    val title: String,
    val content: String,
    val createdAt: LocalDateTime,
    val lastEditedAt: LocalDateTime,
    val postStatus: Int,
    val authorUuid: String,
    val isInstant: Boolean,
    val itinStart: LocalDate,
    val itinFinish: LocalDate,
    val location: String,
    val capacity: Int,
    val joined: Int,
    val compGender: String,
    val compAge: String,
    val compPersonalities: List<String>,
    val compTravelStyles: List<String>,
    val compDiet: List<String>,
    val compEtc: List<String>,
    val planUuid: String?,
) {
    companion object {
        fun of(post: Post, compPreferences: List<CompPreference>): CreatePostResponse {
            return CreatePostResponse(
                uuid = post.uuid.toString(),
                title = post.title,
                content = post.content,
                createdAt = post.createdAt,
                lastEditedAt = post.lastEditedAt,
                postStatus = post.postStatus,
                authorUuid = post.author.uuid.toString(),
                isInstant = post.isInstant,
                itinStart = post.itinStart,
                itinFinish = post.itinFinish,
                location = post.location,
                capacity = post.capacity,
                joined = post.joined,
                planUuid = post.plan?.uuid?.toString(),
                compGender = compPreferences.find { it.preference.type == PreferenceType.GENDER }?.preference?.name.orEmpty(),
                compAge = compPreferences.find { it.preference.type == PreferenceType.AGE }?.preference?.name.orEmpty(),
                compPersonalities = compPreferences.filter { it.preference.type == PreferenceType.PERSONALITY }
                    .map { it.preference.name },
                compTravelStyles = compPreferences.filter { it.preference.type == PreferenceType.TRAVEL_STYLE }
                    .map { it.preference.name },
                compDiet = compPreferences.filter { it.preference.type == PreferenceType.DIET }
                    .map { it.preference.name },
                compEtc = compPreferences.filter { it.preference.type == PreferenceType.ETC }
                    .map { it.preference.name }
            )
        }
    }
}
