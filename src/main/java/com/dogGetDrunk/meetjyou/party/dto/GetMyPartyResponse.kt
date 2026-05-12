package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.party.PartyProgressStatus
import com.dogGetDrunk.meetjyou.party.PartyRecruitmentStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import java.time.Instant
import java.util.UUID

data class GetMyPartyResponse(
    val partyUuid: UUID,
    val roomUuid: UUID,
    val name: String,
    val destination: String,
    val itinStart: Instant,
    val itinFinish: Instant,
    val joined: Int,
    val capacity: Int,
    val progressStatus: PartyProgressStatus,
    val recruitmentStatus: PartyRecruitmentStatus,
    val role: PartyRole,
    val joinedAt: Instant,
    val createdAt: Instant,
    val lastEditedAt: Instant,
    val planUuid: UUID?,
) {
    companion object {
        fun of(userParty: UserParty, chatRoom: ChatRoom): GetMyPartyResponse {
            val party = userParty.party
            return GetMyPartyResponse(
                partyUuid = party.uuid,
                roomUuid = chatRoom.uuid,
                name = party.name,
                destination = party.destination,
                itinStart = party.itinStart,
                itinFinish = party.itinFinish,
                joined = party.joined,
                capacity = party.capacity,
                progressStatus = party.progressStatus,
                recruitmentStatus = party.recruitmentStatus,
                role = userParty.role,
                joinedAt = userParty.joinedAt,
                createdAt = party.createdAt,
                lastEditedAt = party.lastEditedAt,
                planUuid = party.plan?.uuid,
            )
        }
    }
}
