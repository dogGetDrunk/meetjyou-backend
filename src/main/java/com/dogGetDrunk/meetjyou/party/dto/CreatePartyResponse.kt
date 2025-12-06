package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.party.Party
import java.time.LocalDateTime
import java.util.UUID

data class CreatePartyResponse(
    val uuid: UUID,
    val name: String,
    val location: String,
    val createdAt: LocalDateTime
) {
    companion object {
        fun of(party: Party) = CreatePartyResponse(
            uuid = party.uuid,
            name = party.name,
            location = party.destination,
            createdAt = party.createdAt
        )
    }
}
