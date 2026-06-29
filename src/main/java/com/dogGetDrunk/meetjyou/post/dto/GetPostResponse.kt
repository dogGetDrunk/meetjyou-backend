package com.dogGetDrunk.meetjyou.post.dto

import com.dogGetDrunk.meetjyou.plan.dto.GetPlanResponse
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostStatus
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import java.time.Instant
import java.util.UUID

data class GetPostResponse(
    val uuid: UUID,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val lastEditedAt: Instant,
    val postStatus: PostStatus,
    val views: Long,
    val authorUuid: UUID,
    val isInstant: Boolean,
    val itinStart: Instant,
    val itinFinish: Instant,
    val location: String,
    val capacity: Int,
    val joined: Int,
    val companionSpec: CompanionSpec?,
    val partyUuid: UUID,
    val planUuid: UUID?,
    val isPlanPublic: Boolean?,
    val plan: GetPlanResponse?,
    val myApplicationStatus: MemberStatus?,
) {
    companion object {
        fun of(
            post: Post,
            companionSpec: CompanionSpec?,
            views: Long = 0L,
            plan: GetPlanResponse? = null,
            myApplicationStatus: MemberStatus? = null,
        ): GetPostResponse {
            return GetPostResponse(
                uuid = post.uuid,
                title = post.title,
                content = post.content,
                createdAt = post.createdAt,
                lastEditedAt = post.lastEditedAt,
                postStatus = post.status,
                views = views,
                authorUuid = post.author.uuid,
                isInstant = post.isInstant,
                itinStart = post.itinStart,
                itinFinish = post.itinFinish,
                location = post.location,
                capacity = post.capacity,
                joined = post.joined,
                companionSpec = companionSpec,
                partyUuid = post.party.uuid,
                planUuid = post.plan?.uuid,
                isPlanPublic = post.isPlanPublic,
                plan = plan,
                myApplicationStatus = myApplicationStatus,
            )
        }
    }
}
