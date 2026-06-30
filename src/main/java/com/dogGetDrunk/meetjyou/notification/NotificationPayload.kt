package com.dogGetDrunk.meetjyou.notification

data class NotificationPayload(
    val type: NotificationType,
    val titleArgs: Map<String, String> = emptyMap(),
    val bodyArgs: Map<String, String> = emptyMap(),
    val data: Map<String, String> = emptyMap(),
    val dedupKey: String? = null
)
