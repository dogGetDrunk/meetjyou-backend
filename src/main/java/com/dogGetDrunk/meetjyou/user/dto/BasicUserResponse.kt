package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.user.AuthProvider
import java.util.UUID

data class BasicUserResponse(
    val uuid: UUID,
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
