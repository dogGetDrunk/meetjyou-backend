package com.dogGetDrunk.meetjyou.notification.dispatcher

import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox.DeliveryStatus
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.sender.PushNotificationSender
import com.dogGetDrunk.meetjyou.notification.sender.SendResult
import com.dogGetDrunk.meetjyou.notification.target.NotificationTargetResolver
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class NotificationDispatcherTest : BehaviorSpec() {
    private val outboxRepository = mockk<NotificationOutboxRepository>(relaxed = true)
    private val targetResolver = mockk<NotificationTargetResolver>()
    private val sender = mockk<PushNotificationSender>()
    private val objectMapper = ObjectMapper()
    private val sut = NotificationDispatcher(outboxRepository, targetResolver, sender, objectMapper)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun outbox() = NotificationOutbox(
        user = UserFixtures.user(),
        type = NotificationType.CHAT_MESSAGE,
        dataJson = "{}",
    )

    init {
        beforeEach { clearAllMocks() }

        given("dispatchBatch 호출 시") {

            `when`("processItem이 예외를 던지면") {
                then("해당 item이 PENDING으로 재전환된다") {
                    val item = outbox()
                    every { outboxRepository.lockNextPendings(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } throws RuntimeException("FCM error")

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.PENDING, item.attempts, item.availableAt)
                    }
                    verify(exactly = 0) {
                        outboxRepository.updateResult(any(), DeliveryStatus.SENT, any(), any())
                    }
                }
            }

            `when`("첫 번째 item이 예외를 던지고 두 번째 item은 정상이면") {
                then("첫 번째는 PENDING, 두 번째는 SENT로 처리된다") {
                    val failItem = outbox()
                    val okItem = outbox()
                    every { outboxRepository.lockNextPendings(any()) } returns listOf(failItem, okItem)
                    every { targetResolver.resolveUserTargets(any()) } throws RuntimeException("FCM error") andThen listOf("token-abc")
                    every { sender.send(any(), any(), any(), any()) } returns SendResult(ok = true)

                    sut.dispatchBatch(2)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(any(), DeliveryStatus.PENDING, any(), any())
                    }
                    verify(exactly = 1) {
                        outboxRepository.updateResult(any(), DeliveryStatus.SENT, any(), any())
                    }
                }
            }

            `when`("item이 없으면") {
                then("아무 처리도 하지 않는다") {
                    every { outboxRepository.lockNextPendings(any()) } returns emptyList()

                    sut.dispatchBatch(10)

                    verify(exactly = 0) { outboxRepository.updateResult(any(), any(), any(), any()) }
                }
            }
        }
    }
}
