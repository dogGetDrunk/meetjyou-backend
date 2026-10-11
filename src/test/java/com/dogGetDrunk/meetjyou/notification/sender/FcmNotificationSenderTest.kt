package com.dogGetDrunk.meetjyou.notification.sender

import com.google.firebase.ErrorCode
import com.google.firebase.FirebaseException
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

class FcmNotificationSenderTest : BehaviorSpec() {
    private val messaging = mockk<FirebaseMessaging>()
    private val sut = FcmNotificationSender()

    // The SDK only exposes a package-private factory, so build the real exception reflectively
    // instead of mocking it: the base and messaging error codes must differ like they do in production.
    private fun fcmException(base: ErrorCode, messagingCode: MessagingErrorCode?): FirebaseMessagingException {
        val factory = FirebaseMessagingException::class.java.getDeclaredMethod(
            FACTORY_METHOD,
            FirebaseException::class.java,
            MessagingErrorCode::class.java,
        )
        factory.isAccessible = true
        return factory.invoke(null, FirebaseException(base, "fcm error", null), messagingCode)
            as FirebaseMessagingException
    }

    private fun send(): SendResult = sut.send(TOKEN, "title", "body", emptyMap())

    init {
        beforeSpec {
            mockkStatic(FirebaseMessaging::class)
            every { FirebaseMessaging.getInstance() } returns messaging
        }
        afterSpec { unmockkStatic(FirebaseMessaging::class) }

        Given("FCM이 메시지를 수락하면") {
            every { messaging.send(any<Message>()) } returns MESSAGE_ID

            Then("ok와 messageId를 돌려준다") {
                send() shouldBe SendResult(ok = true, messageId = MESSAGE_ID)
            }
        }

        Given("토큰이 등록 해제됐으면(UNREGISTERED, 기본 코드는 NOT_FOUND)") {
            every { messaging.send(any<Message>()) } throws
                fcmException(ErrorCode.NOT_FOUND, MessagingErrorCode.UNREGISTERED)

            Then("영구 실패이면서 토큰 무효로 분류한다") {
                val result = send()
                result.ok shouldBe false
                result.permanent shouldBe true
                result.tokenInvalid shouldBe true
            }
        }

        Given("INVALID_ARGUMENT이면") {
            every { messaging.send(any<Message>()) } throws
                fcmException(ErrorCode.INVALID_ARGUMENT, MessagingErrorCode.INVALID_ARGUMENT)

            Then("영구 실패지만 토큰은 무효로 보지 않는다") {
                val result = send()
                result.ok shouldBe false
                result.permanent shouldBe true
                result.tokenInvalid shouldBe false
            }
        }

        Given("일시 장애(UNAVAILABLE)이면") {
            every { messaging.send(any<Message>()) } throws
                fcmException(ErrorCode.UNAVAILABLE, MessagingErrorCode.UNAVAILABLE)

            Then("재시도 가능한 실패로 분류한다") {
                val result = send()
                result.ok shouldBe false
                result.permanent shouldBe false
                result.tokenInvalid shouldBe false
            }
        }

        Given("messaging 코드가 없는 NOT_FOUND이면") {
            every { messaging.send(any<Message>()) } throws fcmException(ErrorCode.NOT_FOUND, null)

            Then("토큰을 무효로 보지 않고 재시도 가능한 실패로 분류한다") {
                val result = send()
                result.permanent shouldBe false
                result.tokenInvalid shouldBe false
            }
        }

        Given("FCM 예외가 아닌 오류가 나면") {
            every { messaging.send(any<Message>()) } throws IllegalStateException("boom")

            Then("재시도 가능한 실패로 분류한다") {
                send() shouldBe SendResult(ok = false, permanent = false, error = "boom")
            }
        }
    }

    private companion object {
        const val TOKEN = "token-abcdefghij"
        const val MESSAGE_ID = "projects/meetjyou/messages/1"
        const val FACTORY_METHOD = "withMessagingErrorCode"
    }
}
