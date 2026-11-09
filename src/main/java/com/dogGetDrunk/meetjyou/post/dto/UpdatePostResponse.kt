package com.dogGetDrunk.meetjyou.post.dto

import com.dogGetDrunk.meetjyou.place.dto.PlaceSummaryResponse
import com.dogGetDrunk.meetjyou.place.dto.RegionResponse
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostStatus
import java.time.Instant
import java.util.UUID

data class UpdatePostResponse(
    val uuid: UUID,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val lastEditedAt: Instant,
    val postStatus: PostStatus,
    val authorUuid: UUID,
    val isInstant: Boolean,
    val itinStart: Instant,
    val itinFinish: Instant,
    val location: String,
    val region: RegionResponse,
    val place: PlaceSummaryResponse?,
    val capacity: Int,
    val joined: Int,
    val companionSpec: CompanionSpec?,
    val planUuid: UUID?,
    val isPlanPublic: Boolean?,
) {
    companion object {
        fun of(post: Post, region: RegionResponse, companionSpec: CompanionSpec?): UpdatePostResponse {
            return UpdatePostResponse(
                uuid = post.uuid,
                title = post.title,
                content = post.content,
                createdAt = post.createdAt,
                lastEditedAt = post.lastEditedAt,
                postStatus = post.status,
                authorUuid = post.author.uuid,
                isInstant = post.isInstant,
                itinStart = post.itinStart,
                itinFinish = post.itinFinish,
                location = post.location,
                region = region,
                place = post.place?.let { PlaceSummaryResponse.of(it) },
                capacity = post.capacity,
                joined = post.party.joined,
                companionSpec = companionSpec,
                planUuid = post.plan?.uuid,
                isPlanPublic = post.isPlanPublic,
            )
        }
    }
}
