package com.dogGetDrunk.meetjyou.notification.preference

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
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
) {
    @Transactional(readOnly = true)
    fun getSettings(): NotificationSettingsResponse {
        val uuid = SecurityUtil.getCurrentUserUuid()
        val user = userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)

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
        val uuid = SecurityUtil.getCurrentUserUuid()
        val user = userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)

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
