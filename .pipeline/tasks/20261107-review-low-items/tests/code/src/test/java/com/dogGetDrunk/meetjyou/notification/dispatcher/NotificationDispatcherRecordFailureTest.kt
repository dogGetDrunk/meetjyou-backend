package com.dogGetDrunk.meetjyou.notification.dispatcher

import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox.DeliveryStatus
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.push.PushTokenRepository
import com.dogGetDrunk.meetjyou.notification.sender.PushNotificationSender
import com.dogGetDrunk.meetjyou.notification.sender.SendResult
import com.dogGetDrunk.meetjyou.notification.target.NotificationTargetResolver
import com.dogGetDrunk.meetjyou.notification.target.PushTarget
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// AT-004-2: a failure while recording one item's result must not cut the rest of the batch.
class NotificationDispatcherRecordFailureTest : BehaviorSpec() {
    private val outboxRepository = mockk<NotificationOutboxRepository>(relaxed = true)
    private val targetResolver = mockk<NotificationTargetResolver>()
    private val pushTokenRepository = mockk<PushTokenRepository>(relaxed = true)
    private val sender = mockk<PushNotificationSender>()
    private val transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    private val sut = NotificationDispatcher(
        outboxRepository, targetResolver, pushTokenRepository, sender, ObjectMapper(), transactionTemplate,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val tokenFirst = PushTarget(TOKEN_ID_FIRST, "token-first")
    private val tokenInvalid = PushTarget(TOKEN_ID_INVALID, "token-invalid")
    private val tokenGood = PushTarget(TOKEN_ID_GOOD, "token-good")
    private val tokenThird = PushTarget(TOKEN_ID_THIRD, "token-third")

    private fun setField(target: Any, name: String, value: Any) {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name == name }
            if (field != null) {
                field.isAccessible = true
                field.set(target, value)
                return
            }
            type = type.superclass
        }
        error("Field $name not found on ${target.javaClass}")
    }

    // The entity ids are all 0 by default, so give each item and its user a distinct id.
    private fun outbox(outboxId: Long, userId: Long, attempts: Int = 0): NotificationOutbox {
        val user = UserFixtures.user()
        setField(user, "id", userId)
        val item = NotificationOutbox(
            user = user,
            type = NotificationType.CHAT_MESSAGE,
            dataJson = "{}",
            attempts = attempts,
        )
        setField(item, "id", outboxId)
        return item
    }

    private fun stubBatch(vararg items: NotificationOutbox) {
        every { outboxRepository.lockNextClaimable(any()) } returns items.toList()
        every { targetResolver.resolveUserTargets(any()) } returns mapOf(
            USER_ID_FIRST to listOf(tokenFirst),
            USER_ID_SECOND to listOf(tokenInvalid, tokenGood),
            USER_ID_THIRD to listOf(tokenThird),
        )
    }

    private fun stubSend(target: PushTarget, result: SendResult) {
        every { sender.send(target.token, any(), any(), any()) } returns result
    }

    private fun stubHealthySecondAndThird() {
        stubSend(tokenInvalid, SendResult(ok = false, permanent = true, tokenInvalid = true))
        stubSend(tokenGood, SendResult(ok = true))
        stubSend(tokenThird, SendResult(ok = true))
    }

    private fun dispatchExpectingNoThrow() {
        val failure = runCatching { sut.dispatchBatch(BATCH_SIZE) }.exceptionOrNull()
        check(failure == null) { "dispatchBatch must not propagate a record failure: $failure" }
    }

    private fun verifySent(outboxId: Long) {
        verify(exactly = 1) {
            outboxRepository.updateResult(outboxId, DeliveryStatus.SENT, 1, any(), any())
        }
    }

    init {
        beforeEach { clearAllMocks() }

        given("AT-004-2 case A: the retry record of the first item throws") {
            `when`("dispatchBatch runs three items") {
                then("the batch continues, the others are recorded SENT and invalid tokens are deactivated once") {
                    val first = outbox(OUTBOX_ID_FIRST, USER_ID_FIRST)
                    val second = outbox(OUTBOX_ID_SECOND, USER_ID_SECOND)
                    val third = outbox(OUTBOX_ID_THIRD, USER_ID_THIRD)
                    stubBatch(first, second, third)
                    every { sender.send(tokenFirst.token, any(), any(), any()) } throws RuntimeException("FCM error")
                    stubHealthySecondAndThird()
                    every {
                        outboxRepository.updateResult(OUTBOX_ID_FIRST, any(), any(), any(), any())
                    } throws RuntimeException("record failed")

                    dispatchExpectingNoThrow()

                    verifySent(OUTBOX_ID_SECOND)
                    verifySent(OUTBOX_ID_THIRD)
                    verify(exactly = 1) {
                        pushTokenRepository.deactivateAllByIdIn(match { TOKEN_ID_INVALID in it })
                    }
                }
            }
        }

        given("AT-004-2 case B: the DEAD record of an exhausted first item throws") {
            `when`("dispatchBatch runs three items") {
                then("the exception does not propagate and the other two are sent and recorded SENT") {
                    val first = outbox(OUTBOX_ID_FIRST, USER_ID_FIRST, attempts = ATTEMPT_LIMIT)
                    val second = outbox(OUTBOX_ID_SECOND, USER_ID_SECOND)
                    val third = outbox(OUTBOX_ID_THIRD, USER_ID_THIRD)
                    stubBatch(first, second, third)
                    stubHealthySecondAndThird()
                    every {
                        outboxRepository.updateResult(OUTBOX_ID_FIRST, DeliveryStatus.DEAD, any(), any(), any())
                    } throws RuntimeException("record failed")

                    dispatchExpectingNoThrow()

                    verify(exactly = 1) { sender.send(tokenGood.token, any(), any(), any()) }
                    verify(exactly = 1) { sender.send(tokenThird.token, any(), any(), any()) }
                    verifySent(OUTBOX_ID_SECOND)
                    verifySent(OUTBOX_ID_THIRD)
                }
            }
        }

        given("AT-004-2 case C: every item succeeds") {
            `when`("dispatchBatch runs three items") {
                then("each item is recorded SENT as before") {
                    val first = outbox(OUTBOX_ID_FIRST, USER_ID_FIRST)
                    val second = outbox(OUTBOX_ID_SECOND, USER_ID_SECOND)
                    val third = outbox(OUTBOX_ID_THIRD, USER_ID_THIRD)
                    stubBatch(first, second, third)
                    stubSend(tokenFirst, SendResult(ok = true))
                    stubHealthySecondAndThird()

                    dispatchExpectingNoThrow()

                    verifySent(OUTBOX_ID_FIRST)
                    verifySent(OUTBOX_ID_SECOND)
                    verifySent(OUTBOX_ID_THIRD)
                    verify(exactly = 0) {
                        outboxRepository.updateResult(any(), DeliveryStatus.DEAD, any(), any(), any())
                    }
                }
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 3
        const val ATTEMPT_LIMIT = 5
        const val OUTBOX_ID_FIRST = 101L
        const val OUTBOX_ID_SECOND = 102L
        const val OUTBOX_ID_THIRD = 103L
        const val USER_ID_FIRST = 11L
        const val USER_ID_SECOND = 12L
        const val USER_ID_THIRD = 13L
        const val TOKEN_ID_FIRST = 21L
        const val TOKEN_ID_INVALID = 22L
        const val TOKEN_ID_GOOD = 23L
        const val TOKEN_ID_THIRD = 24L
    }
}
