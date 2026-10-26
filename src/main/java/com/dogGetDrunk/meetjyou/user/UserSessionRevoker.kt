package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.notification.push.PushTokenRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Cuts a user off from every device: refresh tokens, push tokens and open chat WebSockets (closed
 * after commit via [UserSessionsRevokedEvent]). Runs inside the caller's transaction (the bulk
 * queries need one).
 */
@Component
class UserSessionRevoker(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    // Refresh tokens are only revoked so reuse detection keeps working; push tokens are deleted so a
    // withdrawn account stops receiving pushes. A restored account registers its push token again.
    fun revokeAll(user: User) {
        refreshTokenRepository.revokeAllByUser(user)
        pushTokenRepository.deleteAllByUserIdIn(listOf(user.id))
        eventPublisher.publishEvent(UserSessionsRevokedEvent(user.uuid))
    }

    fun deleteAll(userIds: Collection<Long>) {
        refreshTokenRepository.deleteAllByUserIdIn(userIds)
        pushTokenRepository.deleteAllByUserIdIn(userIds)
    }
}
