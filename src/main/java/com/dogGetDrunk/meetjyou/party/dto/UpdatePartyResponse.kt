package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.party.Party
import java.time.LocalDateTime
import java.util.UUID

data class UpdatePartyResponse(
    val uuid: UUID,
    val name: String,
    val lastEditedAt: LocalDateTime
) {
    companion object {
        fun of(party: Party) = UpdatePartyResponse(
            uuid = party.uuid,
            name = party.name,
            lastEditedAt = party.lastEditedAt,
        )
    }
}
