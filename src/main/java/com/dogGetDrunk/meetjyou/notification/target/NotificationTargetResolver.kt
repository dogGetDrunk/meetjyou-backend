package com.dogGetDrunk.meetjyou.notification.target

interface NotificationTargetResolver {
    fun resolveUserTargets(userIds: Collection<Long>): Map<Long, List<String>>
}
