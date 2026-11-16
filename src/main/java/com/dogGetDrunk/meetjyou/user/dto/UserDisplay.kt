package com.dogGetDrunk.meetjyou.user.dto

import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserStatus

/**
 * How another user appears next to shared content (post author, chat sender, party member,
 * applicant). The single rule every such response uses, so the screens never disagree (#207).
 *
 * Masked only once the account is purged: a user in the grace period (DELETED) can still restore,
 * so they keep their nickname and photo here, by team decision. The public profile is stricter on
 * purpose and returns 404 from DELETED on.
 */
data class UserDisplay(
    val nickname: String,
    val hasProfileImage: Boolean,
    val isWithdrawn: Boolean,
) {
    companion object {
        // Non-null on purpose: a client that misses the withdrawn case still renders sane text.
        // Parentheses and the space are outside the nickname pattern, so no real user can register it.
        const val WITHDRAWN_NICKNAME = "(탈퇴한 사용자)"

        fun of(user: User): UserDisplay {
            if (user.status == UserStatus.PURGED) {
                return UserDisplay(nickname = WITHDRAWN_NICKNAME, hasProfileImage = false, isWithdrawn = true)
            }
            return UserDisplay(nickname = user.nickname, hasProfileImage = user.hasProfileImage, isWithdrawn = false)
        }
    }
}
