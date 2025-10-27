package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Diet
import com.dogGetDrunk.meetjyou.preference.Etc
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.TravelStyle
import com.dogGetDrunk.meetjyou.user.AuthProvider
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.UniqueElements
import java.time.LocalDate

data class RegistrationRequest(
    @field:Email
    val email: String,
    @field:Size(min = 2, max = 8)
    @field:NotBlank
    @field:Pattern(regexp = "^[a-zA-Z0-9가-힣_]+$") // 특수문자 포함 불가
    val nickname: String,
    @field:Size(max = 30)
    val bio: String?,
    val birthDate: LocalDate,
    val gender: Gender,
    val age: Age,
    @field:Size(max = 3)
    @field:UniqueElements
    val personalities: List<Personality>,
    @field:UniqueElements
    @field:Size(max = 3)
    val travelStyles: List<TravelStyle>,
    @field:UniqueElements
    val diet: List<Diet>,
    @field:UniqueElements
    val etc: List<Etc>,
    val authProvider: AuthProvider,
    val credential: String,
)

fun String?.normalizeOrNull(): String? =
    this?.trim()
        ?.takeIf { it.isNotEmpty() }
