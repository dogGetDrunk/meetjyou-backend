package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.party.Party
import java.time.LocalDate
import java.time.LocalDateTime

data class GetPartyResponse(
    val uuid: String,
    val itinStart: LocalDate,
    val itinFinish: LocalDate,
    val location: String,
    val joined: Int,
    val capacity: Int,
    val name: String,
    val createdAt: LocalDateTime,
    val lastEditedAt: LocalDateTime,
    val planUuid: String?
) {
    companion object {
        fun of(party: Party): GetPartyResponse {
            return GetPartyResponse(
                uuid = party.uuid.toString(),
                itinStart = party.itinStart,
                itinFinish = party.itinFinish,
                location = party.location,
                joined = party.joined,
                capacity = party.capacity,
                name = party.name,
                createdAt = party.createdAt,
                lastEditedAt = party.lastEditedAt,
                planUuid = party.plan?.uuid.toString(),
            )
        }
    }
}
