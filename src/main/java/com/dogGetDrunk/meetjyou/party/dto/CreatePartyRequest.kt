package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.post.Post
import java.time.LocalDate
import java.util.UUID

data class CreatePartyRequest(
    val itinStart: LocalDate,
    val itinFinish: LocalDate,
    val destination: String,
    val joined: Int,
    val capacity: Int,
    val name: String,
    val postUuid: UUID,
    val planUuid: UUID?,
    val ownerUuid: UUID,
) {
    companion object {
        fun from(post: Post): CreatePartyRequest {
            return CreatePartyRequest(
                itinStart = post.itinStart,
                itinFinish = post.itinFinish,
                destination = post.location,
                capacity = post.capacity,
                joined = 1,
                name = post.title,
                postUuid = post.uuid,
                planUuid = post.plan?.uuid,
                ownerUuid = post.author.uuid,
            )
        }
    }
}
