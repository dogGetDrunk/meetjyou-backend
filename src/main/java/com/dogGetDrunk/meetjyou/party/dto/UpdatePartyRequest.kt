package com.dogGetDrunk.meetjyou.party.dto

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class UpdatePartyRequest(
    val itinStart: Instant,
    val itinFinish: Instant,
    @field:NotBlank
    val destination: String,
    @field:Min(1)
    val joined: Int,
    @field:Min(1)
    @field:Max(10)
    val capacity: Int,
    @field:Size(max = 20)
    @field:NotBlank
    val name: String,
    val planUuid: UUID?
) {

    @AssertTrue(message = "일정 시작 시각은 현재 시각 이후여야 합니다.")
    fun isItinStartAfterNow(): Boolean =
        itinStart.isAfter(Instant.now())

    @AssertTrue(message = "일정 종료 시각은 일정 시작 시각 이후여야 합니다.")
    fun isItinFinishAfterItinStart(): Boolean =
        itinFinish.isAfter(itinStart)
}
