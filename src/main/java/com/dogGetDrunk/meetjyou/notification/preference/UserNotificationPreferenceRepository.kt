package com.dogGetDrunk.meetjyou.notification.preference

import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserNotificationPreferenceRepository : JpaRepository<UserNotificationPreference, Long> {
    fun findAllByUser(user: User): List<UserNotificationPreference>
    fun findByUserAndNotificationType(user: User, notificationType: NotificationType): UserNotificationPreference?

    @Modifying
    @Query("DELETE FROM UserNotificationPreference p WHERE p.user.id IN :userIds")
    fun deleteAllByUserIdIn(@Param("userIds") userIds: Collection<Long>): Int
}
