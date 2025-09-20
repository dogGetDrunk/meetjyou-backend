package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Diet
import com.dogGetDrunk.meetjyou.preference.Etc
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.TravelStyle
import com.dogGetDrunk.meetjyou.user.AuthProvider
import java.time.LocalDate

data class RegistrationRequest(
    val email: String,
    val nickname: String,
    val bio: String?,
    val birthDate: LocalDate,
    val gender: Gender,
    val age: Age,
    val personalities: List<Personality>,
    val travelStyles: List<TravelStyle>,
    val diet: Diet,
    val etc: List<Etc>,
    val authProvider: AuthProvider
)

fun String?.normalizeOrNull(): String? =
    this?.trim()
        ?.takeIf { it.isNotEmpty() }
