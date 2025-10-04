package com.dogGetDrunk.meetjyou.post.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class CreatePostRequest(
    @field:Size(max = 20)
    @field:NotBlank
    val title: String,
    @field:Size(max = 500)
    @field:NotBlank
    val content: String,
    val isInstant: Boolean,
    val itinStart: LocalDate,
    val itinFinish: LocalDate,
    val location: String,
    @field:Max(10)
    val capacity: Int,
    @field:Valid
    val companionSpec: CompanionSpec?,
    val authorUuid: UUID,
    val planUuid: UUID?,
    val isPlanPublic: Boolean?,
)
