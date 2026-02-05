package com.dogGetDrunk.meetjyou.post.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

data class UpdatePostRequest(
    @field:Size(max = 20)
    @field:NotBlank
    val title: String,
    @field:Size(max = 500)
    @field:NotBlank
    val content: String,
    val isInstant: Boolean,
    val itinStart: LocalDateTime,
    val itinFinish: LocalDateTime,
    val location: String,
    @field:Min(1)
    @field:Max(10)
    val capacity: Int,
    @field:Valid
    val companionSpec: CompanionSpec?,
    val authorUuid: UUID,
    val planUuid: UUID?,
    val isPlanPublic: Boolean?,
) {

    @AssertTrue(message = "일정 시작 시각은 현재 시각 이후여야 합니다.")
    fun isItinStartAfterNow(): Boolean =
        itinStart.isAfter(LocalDateTime.now())

    @AssertTrue(message = "일정 종료 시각은 일정 시작 시각 이후여야 합니다.")
    fun isItinFinishAfterItinStart(): Boolean =
        itinFinish.isAfter(itinStart)
}
