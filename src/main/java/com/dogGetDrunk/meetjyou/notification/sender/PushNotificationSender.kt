package com.dogGetDrunk.meetjyou.notification.sender

interface PushNotificationSender {
    fun send(token: String, title: String, body: String, data: Map<String, String>): SendResult
}
