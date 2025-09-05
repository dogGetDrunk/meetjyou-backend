package com.dogGetDrunk.meetjyou.notification.target

interface NotificationTargetResolver {
    fun resolveUserTargets(userId: Long): List<String>
}
