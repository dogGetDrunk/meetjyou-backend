package com.dogGetDrunk.meetjyou.notification.target

import com.dogGetDrunk.meetjyou.notification.push.PushTokenRepository
import org.springframework.stereotype.Component

@Component
class DefaultNotificationTargetResolver(
    private val pushTokenRepository: PushTokenRepository,
) : NotificationTargetResolver {
    override fun resolveUserTargets(userId: Long): List<String> {
        return pushTokenRepository.findAllByUserIdAndActiveTrue(userId).map { it.token }
    }
}
