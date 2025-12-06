package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.party.Party
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class GetPartyResponse(
    val uuid: UUID,
    val itinStart: LocalDate,
    val itinFinish: LocalDate,
    val location: String,
    val joined: Int,
    val capacity: Int,
    val name: String,
    val createdAt: LocalDateTime,
    val lastEditedAt: LocalDateTime,
    val planUuid: UUID?
) {
    companion object {
        fun of(party: Party): GetPartyResponse {
            return GetPartyResponse(
                uuid = party.uuid,
                itinStart = party.itinStart,
                itinFinish = party.itinFinish,
                location = party.destination,
                joined = party.joined,
                capacity = party.capacity,
                name = party.name,
                createdAt = party.createdAt,
                lastEditedAt = party.lastEditedAt,
                planUuid = party.plan?.uuid,
            )
        }
    }
}
