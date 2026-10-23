package com.dogGetDrunk.meetjyou.post.dto

import com.dogGetDrunk.meetjyou.user.User
import java.util.UUID

/**
 * Author fields a post card needs, so the client skips one basic-info call per post (#194).
 * Withdrawn authors (DELETED or PURGED) are masked with the same rule as the public profile,
 * which hides them with a 404: a user in the grace period still holds their real nickname.
 */
data class PostAuthorSummary(
    val uuid: UUID,
    val nickname: String?,
    val hasProfileImage: Boolean,
    val isWithdrawn: Boolean,
) {
    companion object {
        fun of(author: User): PostAuthorSummary {
            val active = author.isActive()
            return PostAuthorSummary(
                uuid = author.uuid,
                nickname = author.nickname.takeIf { active },
                hasProfileImage = active && author.hasProfileImage,
                isWithdrawn = !active,
            )
        }
    }
}
