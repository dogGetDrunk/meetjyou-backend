package com.dogGetDrunk.meetjyou.post.dto

import com.dogGetDrunk.meetjyou.post.Post
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class CreatePostResponse(
    val uuid: UUID,
    val title: String,
    val content: String,
    val createdAt: LocalDateTime,
    val lastEditedAt: LocalDateTime,
    val postStatus: Int,
    val authorUuid: UUID,
    val isInstant: Boolean,
    val itinStart: LocalDate,
    val itinFinish: LocalDate,
    val location: String,
    val capacity: Int,
    val joined: Int,
    val companionSpec: CompanionSpec?,
    val planUuid: UUID?,
    val isPlanPublic: Boolean?,
) {
    companion object {
        fun of(post: Post, companionSpec: CompanionSpec?): CreatePostResponse {
            return CreatePostResponse(
                uuid = post.uuid,
                title = post.title,
                content = post.content,
                createdAt = post.createdAt,
                lastEditedAt = post.lastEditedAt,
                postStatus = post.postStatus,
                authorUuid = post.author.uuid,
                isInstant = post.isInstant,
                itinStart = post.itinStart,
                itinFinish = post.itinFinish,
                location = post.location,
                capacity = post.capacity,
                joined = post.joined,
                companionSpec = companionSpec,
                planUuid = post.plan?.uuid,
                isPlanPublic = post.isPlanPublic,
            )
        }
    }
}
