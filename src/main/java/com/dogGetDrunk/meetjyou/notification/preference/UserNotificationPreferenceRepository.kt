package com.dogGetDrunk.meetjyou.notification.preference

import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.user.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserNotificationPreferenceRepository : JpaRepository<UserNotificationPreference, Long> {
    fun findAllByUser(user: User): List<UserNotificationPreference>
    fun findByUserAndNotificationType(user: User, notificationType: NotificationType): UserNotificationPreference?
}
