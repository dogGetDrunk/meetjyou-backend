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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class NotificationDispatcherTest : BehaviorSpec() {
    private val outboxRepository = mockk<NotificationOutboxRepository>(relaxed = true)
    private val targetResolver = mockk<NotificationTargetResolver>()
    private val sender = mockk<PushNotificationSender>()
    private val objectMapper = ObjectMapper()
    private val transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    private val sut = NotificationDispatcher(outboxRepository, targetResolver, sender, objectMapper, transactionTemplate)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun outbox(attempts: Int = 0) = NotificationOutbox(
        user = UserFixtures.user(),
        type = NotificationType.CHAT_MESSAGE,
        dataJson = "{}",
        attempts = attempts,
    )

    init {
        beforeEach { clearAllMocks() }

        given("dispatchBatch 호출 시") {

            `when`("토큰 조회는 성공했지만 FCM 전송이 예외를 던지면") {
                then("해당 item이 attempts를 증가시키고 backoff와 함께 PENDING으로 재전환된다") {
                    val item = outbox()
                    every { outboxRepository.lockNextPendings(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item.user.id to listOf("token-abc"))
                    every { sender.send(any(), any(), any(), any()) } throws RuntimeException("FCM error")

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.PENDING, item.attempts + 1, any())
                    }
                    verify(exactly = 0) {
                        outboxRepository.updateResult(any(), DeliveryStatus.SENT, any(), any())
                    }
                }
            }

            `when`("예외를 던지는 item의 재시도 횟수가 backoff 한도에 도달하면") {
                then("무한 재시도 대신 DEAD로 처리된다") {
                    val item = outbox(attempts = 4) // nextAttempts=5 >= backoffSeconds.size=5
                    every { outboxRepository.lockNextPendings(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item.user.id to listOf("token-abc"))
                    every { sender.send(any(), any(), any(), any()) } throws RuntimeException("FCM error")

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.DEAD, item.attempts + 1, any())
                    }
                }
            }

            `when`("첫 번째 item 전송은 예외를 던지고 두 번째 item은 정상이면") {
                then("첫 번째는 PENDING, 두 번째는 SENT로 처리된다") {
                    val failItem = outbox()
                    val okItem = outbox()
                    every { outboxRepository.lockNextPendings(any()) } returns listOf(failItem, okItem)
                    every { targetResolver.resolveUserTargets(any()) } returns
                        mapOf(failItem.user.id to listOf("token-abc"), okItem.user.id to listOf("token-abc"))
                    every { sender.send(any(), any(), any(), any()) } throws RuntimeException("FCM error") andThen SendResult(ok = true)

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
                then("아무 처리도 하지 않고 토큰도 조회하지 않는다") {
                    every { outboxRepository.lockNextPendings(any()) } returns emptyList()

                    sut.dispatchBatch(10)

                    verify(exactly = 0) { outboxRepository.updateResult(any(), any(), any(), any()) }
                    verify(exactly = 0) { targetResolver.resolveUserTargets(any()) }
                }
            }

            `when`("토큰이 없는 유저의 item이면") {
                then("SENT로 처리된다") {
                    val item = outbox()
                    every { outboxRepository.lockNextPendings(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns emptyMap()

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.SENT, any(), any())
                    }
                }
            }

            `when`("모든 FCM 토큰 전송이 성공하면") {
                then("SENT로 처리된다") {
                    val item = outbox()
                    every { outboxRepository.lockNextPendings(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item.user.id to listOf("token-abc"))
                    every { sender.send(any(), any(), any(), any()) } returns SendResult(ok = true)

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.SENT, any(), any())
                    }
                }
            }

            `when`("FCM이 영구 실패(permanent=true)를 반환하면") {
                then("DEAD로 처리된다") {
                    val item = outbox()
                    every { outboxRepository.lockNextPendings(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item.user.id to listOf("token-abc"))
                    every { sender.send(any(), any(), any(), any()) } returns SendResult(ok = false, permanent = true)

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.DEAD, any(), any())
                    }
                }
            }

            `when`("재시도 횟수가 backoff 한도를 초과하면") {
                then("DEAD로 처리된다") {
                    val item = outbox(attempts = 4) // nextAttempts=5 >= backoffSeconds.size=5
                    every { outboxRepository.lockNextPendings(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item.user.id to listOf("token-abc"))
                    every { sender.send(any(), any(), any(), any()) } returns SendResult(ok = false)

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.DEAD, any(), any())
                    }
                }
            }

            `when`("여러 item이 같은 유저 것이면") {
                then("유저별 토큰 조회를 건별이 아닌 배치로 한 번만 수행한다") {
                    val item1 = outbox()
                    val item2 = outbox()
                    every { outboxRepository.lockNextPendings(any()) } returns listOf(item1, item2)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item1.user.id to listOf("token-abc"))
                    every { sender.send(any(), any(), any(), any()) } returns SendResult(ok = true)

                    sut.dispatchBatch(2)

                    verify(exactly = 1) { targetResolver.resolveUserTargets(any()) }
                }
            }
        }
    }
}
