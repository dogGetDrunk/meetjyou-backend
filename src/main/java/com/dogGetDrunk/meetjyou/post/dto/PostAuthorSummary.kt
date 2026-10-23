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
    val nickname: String,
    val hasProfileImage: Boolean,
    val isWithdrawn: Boolean,
) {
    companion object {
        // Non-null on purpose: a client that misses the withdrawn case still renders sane text
        // instead of failing to decode the whole page. The space keeps it out of the nickname
        // pattern, so no real user can register it.
        const val WITHDRAWN_NICKNAME = "탈퇴한 사용자"

        fun of(author: User): PostAuthorSummary {
            val active = author.isActive()
            return PostAuthorSummary(
                uuid = author.uuid,
                nickname = if (active) author.nickname else WITHDRAWN_NICKNAME,
                hasProfileImage = active && author.hasProfileImage,
                isWithdrawn = !active,
            )
        }
    }
}
