package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.post.Post
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.util.UUID

data class CreatePartyRequest(
    val itinStart: LocalDate,
    val itinFinish: LocalDate,
    val destination: String,
    val joined: Int,
    val capacity: Int,
    val name: String,

    @field:JsonProperty("post_uuid")
    val postUuidString: String,

    @field:JsonProperty("plan_uuid")
    val planUuidString: String?,
) {
    val postUuid: UUID
        get() = UUID.fromString(postUuidString)

    val planUuid: UUID?
        get() = planUuidString?.let { UUID.fromString(it) }

    companion object {
        fun from(post: Post): CreatePartyRequest {
            return CreatePartyRequest(
                itinStart = post.itinStart,
                itinFinish = post.itinFinish,
                destination = post.location,
                capacity = post.capacity,
                joined = 1,
                name = post.title,
                postUuidString = post.uuid.toString(),
                planUuidString = post.plan?.uuid.toString(),
            )
        }
    }
}
