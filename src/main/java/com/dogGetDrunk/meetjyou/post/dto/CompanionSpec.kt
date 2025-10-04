package com.dogGetDrunk.meetjyou.post.dto

import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Diet
import com.dogGetDrunk.meetjyou.preference.Etc
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.TravelStyle
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.UniqueElements

data class CompanionSpec(
    @field:Size(max = 1)
    val gender: List<Gender> = emptyList(),
    @field:Size(max = 1)
    val age: List<Age> = emptyList(),
    @field:Size(max = 3)
    @field:UniqueElements
    val personalities: List<Personality> = emptyList(),
    @field:Size(max = 3)
    @field:UniqueElements
    val travelStyles: List<TravelStyle> = emptyList(),
    @field:UniqueElements
    val diet: List<Diet> = emptyList(),
    @field:UniqueElements
    val etc: List<Etc> = emptyList(),
) {
    fun formalizeTypesToNames(): Map<PreferenceType, List<String>> = buildMap {
        if (gender.isNotEmpty()) {
            put(PreferenceType.GENDER, gender.map { it.name })
        }
        if (age.isNotEmpty()) {
            put(PreferenceType.AGE, age.map { it.name })
        }
        if (personalities.isNotEmpty()) {
            put(PreferenceType.PERSONALITY, personalities.map { it.name })
        }
        if (travelStyles.isNotEmpty()) {
            put(PreferenceType.TRAVEL_STYLE, travelStyles.map { it.name })
        }
        if (diet.isNotEmpty()) {
            put(PreferenceType.DIET, diet.map { it.name })
        }
        if (etc.isNotEmpty()) {
            put(PreferenceType.ETC, etc.map { it.name })
        }
    }
}
