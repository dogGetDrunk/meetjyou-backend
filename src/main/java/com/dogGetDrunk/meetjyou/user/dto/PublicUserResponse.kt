package com.dogGetDrunk.meetjyou.user.dto

import java.util.UUID

data class PublicUserResponse(
    val uuid: UUID,
    val nickname: String,
    val bio: String?,
    val thumbImgUrl: String?,
    val gender: String,
    val age: String,
    val personalities: List<String>,
    val travelStyles: List<String>,
    val diet: List<String>,
    val etc: List<String>,
)
