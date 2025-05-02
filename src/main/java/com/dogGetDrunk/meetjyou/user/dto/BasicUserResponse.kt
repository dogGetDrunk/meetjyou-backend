package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.user.AuthProvider

data class BasicUserResponse(
    val uuid: String,
    val nickname: String,
    val bio: String?,
    val gender: String,
    val age: String,
    val personalities: List<String>,
    val travelStyles: List<String>,
    val diet: String,
    val etc: List<String>,
    val authProvider: AuthProvider
)
