package com.dogGetDrunk.meetjyou.notification.push

import java.util.UUID

interface PushNotificationSender {
    fun sendPushNotification(to: UUID, title: String, message: String)
}
