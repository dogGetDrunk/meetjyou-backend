package com.dogGetDrunk.meetjyou.post.dto

import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Diet
import com.dogGetDrunk.meetjyou.preference.Etc
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.util.UUID

data class CreatePostRequest(
    val title: String,
    val content: String,
    @field:JsonProperty("authorUuid") private val authorUuidString: String,
    val isInstant: Boolean,
    val itinStart: LocalDate,
    val itinFinish: LocalDate,
    val location: String,
    val capacity: Int,
    val compGender: Gender,
    val compAge: Age,
    val compPersonalities: List<Personality>,
    val compTravelStyles: List<Personality>,
    val compDiet: Diet,
    val compEtc: List<Etc>,
    val planId: Long?,
) {
    val authorUuid: UUID
        get() = UUID.fromString(authorUuidString)
}
