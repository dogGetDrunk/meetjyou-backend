package com.dogGetDrunk.meetjyou.post.dto

import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.dto.UserDisplay
import java.util.UUID

/**
 * Author fields a post card needs, so the client skips one basic-info call per post (#194).
 * Withdrawn authors are masked by [UserDisplay], the rule shared with chat and party responses (#207).
 */
data class PostAuthorSummary(
    val uuid: UUID,
    val nickname: String,
    val hasProfileImage: Boolean,
    val isWithdrawn: Boolean,
) {
    companion object {
        fun of(author: User): PostAuthorSummary {
            val display = UserDisplay.of(author)
            return PostAuthorSummary(
                uuid = author.uuid,
                nickname = display.nickname,
                hasProfileImage = display.hasProfileImage,
                isWithdrawn = display.isWithdrawn,
            )
        }
    }
}
