package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.party.PartyProgressStatus
import com.dogGetDrunk.meetjyou.party.PartyRecruitmentStatus
import java.time.LocalDateTime
import java.util.UUID

data class UpdatePartyResponse(
    val uuid: UUID,
    val itinStart: LocalDateTime,
    val itinFinish: LocalDateTime,
    val destination: String,
    val joined: Int,
    val capacity: Int,
    val name: String,
    val createdAt: LocalDateTime,
    val lastEditedAt: LocalDateTime,
    val progressStatus: PartyProgressStatus,
    val recruitmentStatus: PartyRecruitmentStatus,
    val planUuid: UUID?
) {
    companion object {
        fun of(party: Party): UpdatePartyResponse {
            return UpdatePartyResponse(
                uuid = party.uuid,
                itinStart = party.itinStart,
                itinFinish = party.itinFinish,
                destination = party.destination,
                joined = party.joined,
                capacity = party.capacity,
                name = party.name,
                createdAt = party.createdAt,
                lastEditedAt = party.lastEditedAt,
                progressStatus = party.progressStatus,
                recruitmentStatus = party.recruitmentStatus,
                planUuid = party.plan?.uuid,
            )
        }
    }
}

