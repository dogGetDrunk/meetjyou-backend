package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.party.Party
import java.time.LocalDateTime

data class UpdatePartyResponse(
    val uuid: String,
    val name: String,
    val lastEditedAt: LocalDateTime
) {
    companion object {
        fun of(party: Party) = UpdatePartyResponse(
            uuid = party.uuid.toString(),
            name = party.name,
            lastEditedAt = party.lastEditedAt!!
        )
    }
}
