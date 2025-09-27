package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Diet
import com.dogGetDrunk.meetjyou.preference.Etc
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.TravelStyle
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.UniqueElements

data class UserUpdateRequest(
    @field:Size(min = 2, max = 8)
    val nickname: String,
    @field:Size(max = 30)
    val bio: String?,
    val gender: Gender,
    val age: Age,
    @field:Size(max = 3)
    @field:UniqueElements
    val personalities: List<Personality>,
    @field:Size(max = 3)
    @field:UniqueElements
    val travelStyles: List<TravelStyle>,
    @field:UniqueElements
    val diet: List<Diet>,
    @field:UniqueElements
    val etc: List<Etc>
)
