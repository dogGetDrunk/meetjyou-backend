package com.dogGetDrunk.meetjyou.user.dto

data class UserPreferenceData(
    val gender: String,
    val age: String,
    val personalities: List<String>,
    val travelStyles: List<String>,
    val diet: List<String>,
    val etc: List<String>,
)
