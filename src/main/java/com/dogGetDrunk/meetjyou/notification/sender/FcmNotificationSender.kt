package com.dogGetDrunk.meetjyou.notification.sender

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
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
                .send(message)
            log.debug("FCM sent ok: token={}, messageId={}", "${token.take(10)}...", messageId)
            SendResult(ok = true, messageId = messageId)
        } catch (e: FirebaseMessagingException) {
            // INVALID_ARGUMENT can also mean a malformed payload, so only UNREGISTERED marks the token as gone.
            // UNREGISTERED exists only on MessagingErrorCode; the base ErrorCode has no such value.
            val tokenInvalid = e.messagingErrorCode == MessagingErrorCode.UNREGISTERED
            val permanent = tokenInvalid || e.errorCode.name == ERROR_INVALID_ARGUMENT
            log.warn("FCM send failed: token={}, code={}, permanent={}", "${token.take(10)}...", e.errorCode, permanent, e)
            SendResult(ok = false, permanent = permanent, tokenInvalid = tokenInvalid, error = e.errorCode.name)
        } catch (e: Exception) {
            log.error("FCM unexpected error", e)
            SendResult(ok = false, permanent = false, error = e.message)
        }
    }

    private companion object {
        const val ERROR_INVALID_ARGUMENT = "INVALID_ARGUMENT"
    }
}
