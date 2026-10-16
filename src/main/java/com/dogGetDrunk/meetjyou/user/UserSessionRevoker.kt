package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.notification.push.PushTokenRepository
import org.springframework.stereotype.Component

/**
 * Cuts a user off from every device: refresh tokens and push tokens. Runs inside the caller's
 * transaction (the bulk queries need one).
 */
@Component
class UserSessionRevoker(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val pushTokenRepository: PushTokenRepository,
) {
    // Refresh tokens are only revoked so reuse detection keeps working; push tokens are deleted so a
    // withdrawn account stops receiving pushes. A restored account registers its push token again.
    fun revokeAll(user: User) {
        refreshTokenRepository.revokeAllByUser(user)
        pushTokenRepository.deleteAllByUserIdIn(listOf(user.id))
    }

    fun deleteAll(userIds: Collection<Long>) {
        refreshTokenRepository.deleteAllByUserIdIn(userIds)
        pushTokenRepository.deleteAllByUserIdIn(userIds)
    }
}
