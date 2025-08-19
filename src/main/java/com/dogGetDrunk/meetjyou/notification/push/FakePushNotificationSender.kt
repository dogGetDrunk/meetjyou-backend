package com.dogGetDrunk.meetjyou.notification.push

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class FakePushNotificationSender : PushNotificationSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendPushNotification(to: UUID, title: String, message: String) {
        log.info("[푸시 알림] $to 에게 보냄 - 제목: $title, 내용: $message")
    }
}
