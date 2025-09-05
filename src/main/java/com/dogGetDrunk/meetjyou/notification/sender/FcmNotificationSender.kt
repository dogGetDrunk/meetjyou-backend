package com.dogGetDrunk.meetjyou.notification.sender

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FcmNotificationSender : PushNotificationSender {
    private val log = LoggerFactory.getLogger(FcmNotificationSender::class.java)

    override fun send(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>,
    ): SendResult {
        val message = Message.builder()
            .setToken(token)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .putAllData(data)
            .build()

        return try {
            val messageId = FirebaseMessaging.getInstance()
                .send(message) // Admin SDK 경로  [oai_citation:6‡Firebase](https://firebase.google.com/docs/cloud-messaging/send-message?utm_source=chatgpt.com)
            log.debug("FCM sent ok: token={}, messageId={}", token, messageId)
            SendResult(ok = true, messageId = messageId)
        } catch (e: FirebaseMessagingException) {
            val permanent = (e.errorCode.name == "UNREGISTERED" || e.errorCode.name == "INVALID_ARGUMENT")
            log.warn("FCM send failed: token={}, code={}, permanent={}", token, e.errorCode, permanent, e)
            SendResult(ok = false, permanent = permanent, error = e.errorCode.name)
        } catch (e: Exception) {
            log.error("FCM unexpected error", e)
            SendResult(ok = false, permanent = false, error = e.message)
        }
    }
}
