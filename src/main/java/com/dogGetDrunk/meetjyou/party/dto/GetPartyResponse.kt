package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.party.PartyImageState
import com.dogGetDrunk.meetjyou.party.PartyProgressStatus
import com.dogGetDrunk.meetjyou.party.PartyRecruitmentStatus
import com.dogGetDrunk.meetjyou.place.dto.PlaceSummaryResponse
import com.dogGetDrunk.meetjyou.place.dto.RegionResponse
import com.dogGetDrunk.meetjyou.plan.dto.GetPlanResponse
import java.time.Instant
import java.util.UUID

data class GetPartyResponse(
    val uuid: UUID,
    val itinStart: Instant,
    val itinFinish: Instant,
    val destination: String,
    val region: RegionResponse,
    val place: PlaceSummaryResponse?,
    val joined: Int,
    val capacity: Int,
    val name: String,
    val createdAt: Instant,
    val lastEditedAt: Instant,
    val progressStatus: PartyProgressStatus,
    val recruitmentStatus: PartyRecruitmentStatus,
    val planUuid: UUID?,
    val imageState: PartyImageState,
    val planSnapshot: GetPlanResponse?,
) {
    companion object {
        fun of(party: Party, region: RegionResponse, planSnapshot: GetPlanResponse? = null): GetPartyResponse {
            return GetPartyResponse(
                uuid = party.uuid,
                itinStart = party.itinStart,
                itinFinish = party.itinFinish,
                destination = party.destination,
                region = region,
                place = party.place?.let { PlaceSummaryResponse.of(it) },
                joined = party.joined,
                capacity = party.capacity,
                name = party.name,
                createdAt = party.createdAt,
                lastEditedAt = party.lastEditedAt,
                progressStatus = party.progressStatus,
                recruitmentStatus = party.recruitmentStatus,
                planUuid = party.plan?.uuid,
                imageState = party.imageState,
                planSnapshot = planSnapshot,
            )
        }
    }
}
