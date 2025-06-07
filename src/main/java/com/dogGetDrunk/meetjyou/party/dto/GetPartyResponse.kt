package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.party.Party
import java.time.LocalDateTime

data class GetPartyResponse(
    val uuid: String,
    val itinStart: LocalDateTime,
    val itinFinish: LocalDateTime,
    val destination: String,
    val joined: Int,
    val max: Int,
    val name: String,
    val imgUrl: String,
    val thumbImgUrl: String,
    val createdAt: LocalDateTime?,
    val lastEditedAt: LocalDateTime?,
    val planUuid: String?
) {
    companion object {
        fun of(party: Party): GetPartyResponse {
            return GetPartyResponse(
                uuid = party.uuid.toString(),
                itinStart = party.itinStart,
                itinFinish = party.itinFinish,
                destination = party.destination,
                joined = party.joined,
                max = party.max,
                name = party.name,
                imgUrl = party.imgUrl,
                thumbImgUrl = party.thumbImgUrl,
                createdAt = party.createdAt,
                lastEditedAt = party.lastEditedAt,
                planUuid = party.plan?.uuid.toString()
            )
        }
    }
}
