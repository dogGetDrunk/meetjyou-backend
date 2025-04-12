package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Diet
import com.dogGetDrunk.meetjyou.preference.Etc
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.util.UUID

data class RegistrationRequest(
    @field:JsonProperty("uuid") private val uuidString: String,
    val email: String,
    val nickname: String,
    val bio: String?,
    val birthDate: LocalDate,
    val gender: Gender,
    val age: Age,
    val personalities: List<Personality>,
    val travelStyles: List<Personality>,
    val diet: Diet,
    val etc: List<Etc>,
    val authProvider: AuthProvider
) {
    val uuid: UUID
        get() = UUID.fromString(uuidString)
}
