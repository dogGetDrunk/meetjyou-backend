package com.dogGetDrunk.meetjyou.notification.preference

import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.preference.dto.NotificationSettingsResponse
import com.dogGetDrunk.meetjyou.notification.preference.dto.UpdateNotificationSettingsRequest
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationPreferenceService(
    private val userRepository: UserRepository,
    private val preferenceRepository: UserNotificationPreferenceRepository,
    private val currentUserProvider: CurrentUserProvider,
) {
    @Transactional(readOnly = true)
    fun getSettings(): NotificationSettingsResponse {
        val user = currentUserProvider.user

        val persisted = preferenceRepository.findAllByUser(user)
            .associate { it.notificationType to it.enabled }

        val categories = NotificationType.entries.associateWith { type ->
            persisted.getOrDefault(type, true)
        }

        return NotificationSettingsResponse(
            globalEnabled = user.notified,
            categories = categories,
        )
    }

    @Transactional(readOnly = true)
    fun isEnabled(user: User, type: NotificationType): Boolean {
        if (!user.notified) {
            return false
        }
        return preferenceRepository.findByUserAndNotificationType(user, type)?.enabled ?: true
    }

    @Transactional
    fun updateSettings(request: UpdateNotificationSettingsRequest) {
        val user = currentUserProvider.user

        request.globalEnabled?.let { user.notified = it }

        request.categories?.forEach { (type, enabled) ->
            val existing = preferenceRepository.findByUserAndNotificationType(user, type)
            if (existing != null) {
                existing.enabled = enabled
            } else {
                preferenceRepository.save(UserNotificationPreference(user, type, enabled))
            }
        }
    }
}
