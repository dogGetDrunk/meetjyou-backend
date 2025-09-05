package com.dogGetDrunk.meetjyou.notification.event

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import java.util.Locale
import java.util.UUID

data class NotificationEvent(
    val userUuid: UUID,
    val payload: NotificationPayload,
    val preferredLocale: Locale = Locale.KOREAN,
)
