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
import java.time.Instant

class NotificationDispatcherTest : BehaviorSpec() {
    private val outboxRepository = mockk<NotificationOutboxRepository>(relaxed = true)
    private val targetResolver = mockk<NotificationTargetResolver>()
    private val pushTokenRepository = mockk<PushTokenRepository>(relaxed = true)
    private val sender = mockk<PushNotificationSender>()
    private val objectMapper = ObjectMapper()
    private val transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    private val sut = NotificationDispatcher(
        outboxRepository, targetResolver, pushTokenRepository, sender, objectMapper, transactionTemplate,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun outbox(attempts: Int = 0, deliveredTokenIds: String? = null): NotificationOutbox {
        val item = NotificationOutbox(
            user = UserFixtures.user(),
            type = NotificationType.CHAT_MESSAGE,
            dataJson = "{}",
            attempts = attempts,
        )
        item.deliveredTokenIds = deliveredTokenIds
        return item
    }

    private val singleToken = listOf(PushTarget(1L, "token-abc"))

    private val tokenA = PushTarget(1L, "token-a")
    private val tokenB = PushTarget(2L, "token-b")

    private fun stubClaim(vararg items: NotificationOutbox) {
        every { outboxRepository.lockNextClaimable(any()) } returns items.toList()
        every { targetResolver.resolveUserTargets(any()) } returns
            items.associate { it.user.id to listOf(tokenA, tokenB) }
    }

    private fun stubSend(target: PushTarget, result: SendResult) {
        every { sender.send(target.token, any(), any(), any()) } returns result
    }

    init {
        beforeEach { clearAllMocks() }

        given("dispatchBatch 호출 시") {

            `when`("토큰 조회는 성공했지만 FCM 전송이 예외를 던지면") {
                then("해당 item이 attempts를 증가시키고 backoff와 함께 PENDING으로 재전환된다") {
                    val item = outbox()
                    every { outboxRepository.lockNextClaimable(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item.user.id to singleToken)
                    every { sender.send(any(), any(), any(), any()) } throws RuntimeException("FCM error")

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.PENDING, item.attempts + 1, any(), any())
                    }
                    verify(exactly = 0) {
                        outboxRepository.updateResult(any(), DeliveryStatus.SENT, any(), any(), any())
                    }
                }
            }

            `when`("예외를 던지는 item의 재시도 횟수가 backoff 한도에 도달하면") {
                then("무한 재시도 대신 DEAD로 처리된다") {
                    val item = outbox(attempts = 4) // nextAttempts=5 >= backoffSeconds.size=5
                    every { outboxRepository.lockNextClaimable(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item.user.id to singleToken)
                    every { sender.send(any(), any(), any(), any()) } throws RuntimeException("FCM error")

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.DEAD, item.attempts + 1, any(), any())
                    }
                }
            }

            `when`("첫 번째 item 전송은 예외를 던지고 두 번째 item은 정상이면") {
                then("첫 번째는 PENDING, 두 번째는 SENT로 처리된다") {
                    val failItem = outbox()
                    val okItem = outbox()
                    every { outboxRepository.lockNextClaimable(any()) } returns listOf(failItem, okItem)
                    every { targetResolver.resolveUserTargets(any()) } returns
                        mapOf(failItem.user.id to singleToken, okItem.user.id to singleToken)
                    every { sender.send(any(), any(), any(), any()) } throws RuntimeException("FCM error") andThen SendResult(ok = true)

                    sut.dispatchBatch(2)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(any(), DeliveryStatus.PENDING, any(), any(), any())
                    }
                    verify(exactly = 1) {
                        outboxRepository.updateResult(any(), DeliveryStatus.SENT, any(), any(), any())
                    }
                }
            }

            `when`("item이 없으면") {
                then("아무 처리도 하지 않고 토큰도 조회하지 않는다") {
                    every { outboxRepository.lockNextClaimable(any()) } returns emptyList()

                    sut.dispatchBatch(10)

                    verify(exactly = 0) { outboxRepository.updateResult(any(), any(), any(), any(), any()) }
                    verify(exactly = 0) { targetResolver.resolveUserTargets(any()) }
                }
            }

            `when`("토큰이 없는 유저의 item이면") {
                then("SENT로 처리된다") {
                    val item = outbox()
                    every { outboxRepository.lockNextClaimable(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns emptyMap()

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.SENT, any(), any(), any())
                    }
                }
            }

            `when`("모든 FCM 토큰 전송이 성공하면") {
                then("SENT로 처리된다") {
                    val item = outbox()
                    every { outboxRepository.lockNextClaimable(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item.user.id to singleToken)
                    every { sender.send(any(), any(), any(), any()) } returns SendResult(ok = true)

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.SENT, any(), any(), any())
                    }
                }
            }

            `when`("FCM이 영구 실패(permanent=true)를 반환하면") {
                then("DEAD로 처리된다") {
                    val item = outbox()
                    every { outboxRepository.lockNextClaimable(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item.user.id to singleToken)
                    every { sender.send(any(), any(), any(), any()) } returns SendResult(ok = false, permanent = true)

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.DEAD, any(), any(), any())
                    }
                }
            }

            `when`("재시도 횟수가 backoff 한도를 초과하면") {
                then("DEAD로 처리된다") {
                    val item = outbox(attempts = 4) // nextAttempts=5 >= backoffSeconds.size=5
                    every { outboxRepository.lockNextClaimable(any()) } returns listOf(item)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item.user.id to singleToken)
                    every { sender.send(any(), any(), any(), any()) } returns SendResult(ok = false)

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.DEAD, any(), any(), any())
                    }
                }
            }

            `when`("여러 item이 같은 유저 것이면") {
                then("유저별 토큰 조회를 건별이 아닌 배치로 한 번만 수행한다") {
                    val item1 = outbox()
                    val item2 = outbox()
                    every { outboxRepository.lockNextClaimable(any()) } returns listOf(item1, item2)
                    every { targetResolver.resolveUserTargets(any()) } returns mapOf(item1.user.id to singleToken)
                    every { sender.send(any(), any(), any(), any()) } returns SendResult(ok = true)

                    sut.dispatchBatch(2)

                    verify(exactly = 1) { targetResolver.resolveUserTargets(any()) }
                }
            }
        }
        given("claim 단계에서") {

            `when`("대기 중인 item을 집으면") {
                then("상태 변경과 함께 10분 lease를 건다") {
                    val item = outbox()
                    stubClaim(item)
                    stubSend(tokenA, SendResult(ok = true))
                    stubSend(tokenB, SendResult(ok = true))

                    sut.dispatchBatch(1)

                    verify(exactly = 1) { outboxRepository.markClaimed(listOf(item.id), LEASE_SECONDS) }
                }
            }

            `when`("결과 기록 없이 한도만큼 회수된 item이면") {
                then("발송하지 않고 DEAD로 처리한다") {
                    val item = outbox(attempts = 5)
                    stubClaim(item)
                    stubSend(tokenA, SendResult(ok = true))
                    stubSend(tokenB, SendResult(ok = true))

                    sut.dispatchBatch(1)

                    verify(exactly = 0) { sender.send(any(), any(), any(), any()) }
                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.DEAD, any(), any(), any())
                    }
                }
            }
        }

        given("토큰이 여러 개인 유저의 item을 발송할 때") {

            `when`("한 토큰은 성공하고 다른 토큰은 일시 실패하면") {
                then("성공한 토큰 id를 기록하고 backoff 뒤에 PENDING으로 재시도한다") {
                    val item = outbox()
                    stubClaim(item)
                    stubSend(tokenA, SendResult(ok = true))
                    stubSend(tokenB, SendResult(ok = false))
                    val before = Instant.now()

                    sut.dispatchBatch(1)

                    val earliest = before.plusSeconds(FIRST_RETRY_DELAY_SECONDS)
                    verify(exactly = 1) {
                        outboxRepository.updateResult(
                            item.id,
                            DeliveryStatus.PENDING,
                            1,
                            match { !it.isBefore(earliest) && it.isBefore(earliest.plusSeconds(5)) },
                            "[1]",
                        )
                    }
                }
            }

            `when`("이전 시도에서 이미 받은 토큰이 있으면") {
                then("그 토큰은 건너뛰고 나머지에만 발송한 뒤 SENT로 처리한다") {
                    val item = outbox(attempts = 1, deliveredTokenIds = "[1]")
                    stubClaim(item)
                    stubSend(tokenA, SendResult(ok = true))
                    stubSend(tokenB, SendResult(ok = true))

                    sut.dispatchBatch(1)

                    verify(exactly = 0) { sender.send(tokenA.token, any(), any(), any()) }
                    verify(exactly = 1) { sender.send(tokenB.token, any(), any(), any()) }
                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.SENT, 2, any(), "[1,2]")
                    }
                }
            }

            `when`("한 토큰은 성공하고 다른 토큰은 등록 해제(UNREGISTERED)됐으면") {
                then("item은 SENT로 처리하고 해제된 토큰만 비활성화한다") {
                    val item = outbox()
                    stubClaim(item)
                    stubSend(tokenA, SendResult(ok = true))
                    stubSend(tokenB, SendResult(ok = false, permanent = true, tokenInvalid = true))

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.SENT, 1, any(), "[1]")
                    }
                    verify(exactly = 1) { pushTokenRepository.deactivateAllByIdIn(match { it.toSet() == setOf(2L) }) }
                }
            }

            `when`("영구 실패지만 토큰 문제가 아니면(INVALID_ARGUMENT)") {
                then("item은 나머지 결과로 판정하고 토큰은 비활성화하지 않는다") {
                    val item = outbox()
                    stubClaim(item)
                    stubSend(tokenA, SendResult(ok = true))
                    stubSend(tokenB, SendResult(ok = false, permanent = true))

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.SENT, 1, any(), "[1]")
                    }
                    verify(exactly = 0) { pushTokenRepository.deactivateAllByIdIn(any()) }
                }
            }

            `when`("모든 토큰이 등록 해제됐으면") {
                then("item은 DEAD로 처리하고 토큰을 전부 비활성화한다") {
                    val item = outbox()
                    stubClaim(item)
                    stubSend(tokenA, SendResult(ok = false, permanent = true, tokenInvalid = true))
                    stubSend(tokenB, SendResult(ok = false, permanent = true, tokenInvalid = true))

                    sut.dispatchBatch(1)

                    verify(exactly = 1) {
                        outboxRepository.updateResult(item.id, DeliveryStatus.DEAD, 1, any(), null)
                    }
                    verify(exactly = 1) {
                        pushTokenRepository.deactivateAllByIdIn(match { it.toSet() == setOf(1L, 2L) })
                    }
                }
            }

            `when`("여러 item에서 해제된 토큰이 발견되면") {
                then("토큰 비활성화는 배치당 한 번만 호출한다") {
                    val item1 = outbox()
                    val item2 = outbox()
                    stubClaim(item1, item2)
                    stubSend(tokenA, SendResult(ok = true))
                    stubSend(tokenB, SendResult(ok = false, permanent = true, tokenInvalid = true))

                    sut.dispatchBatch(2)

                    verify(exactly = 1) { pushTokenRepository.deactivateAllByIdIn(any()) }
                }
            }
        }
    }

    private companion object {
        const val LEASE_SECONDS = 600L
        const val FIRST_RETRY_DELAY_SECONDS = 15L
    }
}
