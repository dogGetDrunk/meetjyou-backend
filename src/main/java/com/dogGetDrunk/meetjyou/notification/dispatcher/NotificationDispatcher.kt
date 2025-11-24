package com.dogGetDrunk.meetjyou.notification.dispatcher

import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox.DeliveryStatus
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.sender.PushNotificationSender
import com.dogGetDrunk.meetjyou.notification.target.NotificationTargetResolver
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.math.min

@Service
class NotificationDispatcher(
    private val outboxRepository: NotificationOutboxRepository,
    private val targetResolver: NotificationTargetResolver,
    private val sender: PushNotificationSender,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(NotificationDispatcher::class.java)
    private val backoffSeconds = listOf(5L, 15L, 60L, 180L, 600L)

//    @Scheduled(fixedDelay = 2000L) // 2초 간격
//    fun scheduledDispatch() {
//        dispatchBatch(limit = 200)
//    }

    @Transactional
    fun dispatchBatch(limit: Int) {
        val items = outboxRepository.lockNextPendings(limit)
        if (items.isEmpty()) return

        // 선택한 레코드 상태를 SENDING으로 변경(동일 트랜잭션)
        outboxRepository.bulkUpdateStatus(items.map { it.id }, DeliveryStatus.SENDING)

        for (item in items) {
            processItem(item)
        }
    }

    private fun processItem(item: NotificationOutbox) {
        val tokens = targetResolver.resolveUserTargets(item.user.id)
        if (tokens.isEmpty()) {
            mark(item, DeliveryStatus.SENT, item.attempts + 1, null) // 토큰이 없으면 실질적으로 보낼 대상이 없어 완료 처리
            log.info("No tokens for userId={}, mark SENT. outboxId={}", item.user.id, item.id)
            return
        }
        val data = objectMapper.readValue(
            item.dataJson,
            object : TypeReference<Map<String, String>>() {}
        )
        var allOk = true
        var anyPermanentFailure = false

        tokens.forEach { token ->
            val result = sender.send(
                token = token,
                title = item.title ?: "",
                body = item.body ?: "",
                data = data
            )
            if (!result.ok) {
                allOk = false
                if (result.permanent) {
                    anyPermanentFailure = true
                }
            }
        }

        val nextAttempts = item.attempts + 1
        when {
            allOk -> {
                mark(item, DeliveryStatus.SENT, nextAttempts, null)
            }

            anyPermanentFailure || nextAttempts >= backoffSeconds.size -> {
                mark(item, DeliveryStatus.DEAD, nextAttempts, null)
            }

            else -> {
                val delay = backoffSeconds[min(nextAttempts, backoffSeconds.size - 1)]
                mark(item, DeliveryStatus.PENDING, nextAttempts, LocalDateTime.now().plusSeconds(delay))
            }
        }
    }

    private fun mark(item: NotificationOutbox, status: DeliveryStatus, attempts: Int, nextAt: LocalDateTime?) {
        outboxRepository.updateResult(item.id, status, attempts, nextAt)
    }
}
