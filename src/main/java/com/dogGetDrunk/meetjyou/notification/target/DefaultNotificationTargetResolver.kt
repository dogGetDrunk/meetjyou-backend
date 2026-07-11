package com.dogGetDrunk.meetjyou.notification.target

import com.dogGetDrunk.meetjyou.notification.push.PushTokenRepository
import org.springframework.stereotype.Component

@Component
class DefaultNotificationTargetResolver(
    private val pushTokenRepository: PushTokenRepository,
) : NotificationTargetResolver {
    override fun resolveUserTargets(userIds: Collection<Long>): Map<Long, List<String>> {
        if (userIds.isEmpty()) return emptyMap()
        return pushTokenRepository.findAllByUserIdInAndActiveTrue(userIds)
            .groupBy({ it.user.id }, { it.token })
    }
}
