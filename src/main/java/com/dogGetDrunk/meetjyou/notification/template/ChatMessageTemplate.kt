package com.dogGetDrunk.meetjyou.notification.template

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class ChatMessageTemplate : NotificationTemplate {

    override fun makeTitle(locale: Locale, payload: NotificationPayload): String {
        return "[새 메시지]"
    }

    override fun makeBody(locale: Locale, payload: NotificationPayload): String {
        val sender = payload.bodyArgs["senderNickname"] ?: "누군가"
        val message = payload.bodyArgs["message"] ?: ""
        return "$sender: $message"
    }
}
