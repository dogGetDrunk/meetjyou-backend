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
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.math.min

@Service
class NotificationDispatcher(
    private val outboxRepository: NotificationOutboxRepository,
    private val targetResolver: NotificationTargetResolver,
    private val sender: PushNotificationSender,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(NotificationDispatcher::class.java)
    private val backoffSeconds = listOf(5L, 15L, 60L, 180L, 600L)

    @Scheduled(fixedDelay = 2000L)
    fun scheduledDispatch() {
        dispatchBatch(limit = 200)
    }

    fun dispatchBatch(limit: Int) {
        val items = claimBatch(limit)
        if (items.isEmpty()) return

        val tokensByUserId = targetResolver.resolveUserTargets(items.map { it.user.id }.distinct())

        for (item in items) {
            try {
                processItem(item, tokensByUserId[item.user.id] ?: emptyList())
            } catch (e: Exception) {
                log.error("Failed to process outbox item id={}, scheduling retry", item.id, e)
                retryOrGiveUp(item)
            }
        }
    }

    // TransactionTemplate instead of @Transactional: this is called from scheduledDispatch in
    // the same bean, and a self-invocation would silently bypass the transactional proxy. The
    // SKIP LOCKED row locks and the SENDING status change must commit as one unit so another
    // worker cannot pick up the same rows; the FCM I/O afterwards stays outside the transaction.
    private fun claimBatch(limit: Int): List<NotificationOutbox> {
        return transactionTemplate.execute {
            val items = outboxRepository.lockNextPendings(limit)
            if (items.isNotEmpty()) {
                outboxRepository.bulkUpdateStatus(items.map { it.id }, DeliveryStatus.SENDING)
            }
            items
        } ?: emptyList()
    }

    private fun retryOrGiveUp(item: NotificationOutbox) {
        val nextAttempts = item.attempts + 1
        if (nextAttempts >= backoffSeconds.size) {
            mark(item, DeliveryStatus.DEAD, nextAttempts, item.availableAt)
            return
        }
        val delay = backoffSeconds[min(nextAttempts, backoffSeconds.size - 1)]
        mark(item, DeliveryStatus.PENDING, nextAttempts, Instant.now().plusSeconds(delay))
    }

    @Scheduled(fixedDelay = 60_000L)
    fun reportDeadItems() {
        val count = outboxRepository.countByStatus(DeliveryStatus.DEAD)
        if (count > 0) {
            log.warn("Unresolved DEAD notification outbox items: count={}", count)
        }
    }

    private fun processItem(item: NotificationOutbox, tokens: List<String>) {
        if (tokens.isEmpty()) {
            mark(item, DeliveryStatus.SENT, item.attempts + 1, item.availableAt) // no push tokens registered — no recipients to deliver to, mark as sent
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
                mark(item, DeliveryStatus.SENT, nextAttempts, item.availableAt)
            }

            anyPermanentFailure || nextAttempts >= backoffSeconds.size -> {
                mark(item, DeliveryStatus.DEAD, nextAttempts, item.availableAt)
            }

            else -> {
                val delay = backoffSeconds[min(nextAttempts, backoffSeconds.size - 1)]
                mark(item, DeliveryStatus.PENDING, nextAttempts, Instant.now().plusSeconds(delay))
            }
        }
    }

    private fun mark(item: NotificationOutbox, status: DeliveryStatus, attempts: Int, nextAt: Instant?) {
        outboxRepository.updateResult(item.id, status, attempts, nextAt)
    }
}
