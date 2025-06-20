package com.dogGetDrunk.meetjyou.post.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class CreatePostResponse(
    val uuid: String,
    val title: String,
    val content: String,
    val createdAt: LocalDateTime,
    val lastEditedAt: LocalDateTime,
    val postStatus: Int,
    val authorUuid: String,
    val isInstant: Boolean,
    val itinStart: LocalDate,
    val itinFinish: LocalDate,
    val location: String,
    val capacity: Int,
    val joined: Int,
    val compGender: String,
    val compAge: String,
    val compPersonalities: List<String>,
    val compTravelStyles: List<String>,
    val compDiet: String,
    val compEtc: List<String>,
    val planUuid: String?,
)
