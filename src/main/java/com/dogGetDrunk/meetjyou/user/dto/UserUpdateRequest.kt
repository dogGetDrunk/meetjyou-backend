package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Diet
import com.dogGetDrunk.meetjyou.preference.Etc
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.TravelStyle

data class UserUpdateRequest(
    val nickname: String,
    val bio: String?,
    val gender: Gender,
    val age: Age,
    val personalities: List<Personality>,
    val travelStyles: List<TravelStyle>,
    val diet: Diet,
    val etc: List<Etc>
)
