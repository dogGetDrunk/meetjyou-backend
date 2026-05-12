package com.dogGetDrunk.meetjyou.notification.preference.dto

import com.dogGetDrunk.meetjyou.notification.NotificationType

data class UpdateNotificationSettingsRequest(
    val globalEnabled: Boolean?,
    val categories: Map<NotificationType, Boolean>?,
)
