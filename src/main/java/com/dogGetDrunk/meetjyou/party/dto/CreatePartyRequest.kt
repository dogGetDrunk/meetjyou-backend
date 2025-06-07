package com.dogGetDrunk.meetjyou.party.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.UUID

data class CreatePartyRequest(
    val itinStart: LocalDateTime,
    val itinFinish: LocalDateTime,
    val destination: String,
    val joined: Int,
    val max: Int,
    val name: String,
    val imgUrl: String,
    val thumbImgUrl: String,

    @field:JsonProperty("plan_uuid")
    val planUuidString: String
) {
    val planUuid: UUID
        get() = UUID.fromString(planUuidString)
}
