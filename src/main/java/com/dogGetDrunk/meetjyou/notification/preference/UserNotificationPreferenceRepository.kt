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

    // One query for the whole broadcast population; users without a row count as enabled.
    @Query(
        """
        SELECT p.user.id FROM UserNotificationPreference p
         WHERE p.notificationType = :type AND p.enabled = false
        """
    )
    fun findDisabledUserIdsByType(@Param("type") type: NotificationType): Set<Long>

    @Modifying
    @Query("DELETE FROM UserNotificationPreference p WHERE p.user.id IN :userIds")
    fun deleteAllByUserIdIn(@Param("userIds") userIds: Collection<Long>): Int
}
