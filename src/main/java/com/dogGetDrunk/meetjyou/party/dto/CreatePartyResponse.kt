package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.party.Party
import java.time.LocalDateTime

data class CreatePartyResponse(
    val uuid: String,
    val name: String,
    val destination: String,
    val createdAt: LocalDateTime
) {
    companion object {
        fun of(party: Party) = CreatePartyResponse(
            uuid = party.uuid.toString(),
            name = party.name,
            destination = party.destination,
            createdAt = party.createdAt!!
        )
    }
}
