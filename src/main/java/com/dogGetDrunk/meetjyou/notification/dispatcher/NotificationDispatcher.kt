package com.dogGetDrunk.meetjyou.notification.dispatcher

import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox.DeliveryStatus
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.push.PushTokenRepository
import com.dogGetDrunk.meetjyou.notification.sender.PushNotificationSender
import com.dogGetDrunk.meetjyou.notification.target.NotificationTargetResolver
import com.dogGetDrunk.meetjyou.notification.target.PushTarget
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
    private val pushTokenRepository: PushTokenRepository,
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
        val claimed = claimBatch(limit)
        if (claimed.isEmpty()) return

        // A row only reaches the attempt limit without a recorded result when every claim died
        // mid-send; sending it again would repeat whatever keeps killing the worker.
        val (exhausted, items) = claimed.partition { it.attempts >= backoffSeconds.size }
        exhausted.forEach {
            log.warn("Outbox item reclaimed past the attempt limit, mark DEAD without sending. outboxId={}", it.id)
            mark(it, DeliveryStatus.DEAD, it.attempts + 1, it.availableAt, it.deliveredTokenIds)
        }
        if (items.isEmpty()) return

        val targetsByUserId = targetResolver.resolveUserTargets(items.map { it.user.id }.distinct())
        val invalidTokenIds = mutableSetOf<Long>()

        for (item in items) {
            try {
                invalidTokenIds += processItem(item, targetsByUserId[item.user.id] ?: emptyList())
            } catch (e: Exception) {
                log.error("Failed to process outbox item id={}, scheduling retry", item.id, e)
                recordRetry(item, item.attempts + 1, item.deliveredTokenIds)
            }
        }
        deactivateTokens(invalidTokenIds)
    }

    // TransactionTemplate instead of @Transactional: this is called from scheduledDispatch in
    // the same bean, and a self-invocation would silently bypass the transactional proxy. The
    // SKIP LOCKED row locks and the SENDING status change must commit as one unit so another
    // worker cannot pick up the same rows; the FCM I/O afterwards stays outside the transaction.
    // The claim also sets a lease on available_at and counts the attempt up front, so a worker
    // that dies before recording a result neither strands the row nor retries it forever.
    private fun claimBatch(limit: Int): List<NotificationOutbox> {
        return transactionTemplate.execute {
            val items = outboxRepository.lockNextClaimable(limit)
            if (items.isNotEmpty()) {
                outboxRepository.markClaimed(items.map { it.id }, CLAIM_LEASE_SECONDS)
            }
            items
        } ?: emptyList()
    }

    private fun recordRetry(item: NotificationOutbox, nextAttempts: Int, deliveredTokenIds: String?) {
        if (nextAttempts >= backoffSeconds.size) {
            mark(item, DeliveryStatus.DEAD, nextAttempts, item.availableAt, deliveredTokenIds)
            return
        }
        val delay = backoffSeconds[min(nextAttempts, backoffSeconds.size - 1)]
        mark(item, DeliveryStatus.PENDING, nextAttempts, Instant.now().plusSeconds(delay), deliveredTokenIds)
    }

    @Scheduled(fixedDelay = 60_000L)
    fun reportDeadItems() {
        val count = outboxRepository.countByStatus(DeliveryStatus.DEAD)
        if (count > 0) {
            log.warn("Unresolved DEAD notification outbox items: count={}", count)
        }
    }

    // Returns the ids of tokens the provider reported as gone, for one batched deactivation.
    private fun processItem(item: NotificationOutbox, targets: List<PushTarget>): Set<Long> {
        val nextAttempts = item.attempts + 1
        if (targets.isEmpty()) {
            // no push tokens registered — no recipients to deliver to, mark as sent
            mark(item, DeliveryStatus.SENT, nextAttempts, item.availableAt, item.deliveredTokenIds)
            log.info("No tokens for userId={}, mark SENT. outboxId={}", item.user.id, item.id)
            return emptySet()
        }
        val alreadyDelivered = readDeliveredTokenIds(item)
        val outcome = sendToTargets(item, targets.filterNot { it.tokenId in alreadyDelivered })
        val delivered = alreadyDelivered + outcome.deliveredTokenIds
        val deliveredJson = if (delivered.isEmpty()) null else objectMapper.writeValueAsString(delivered.sorted())

        when {
            outcome.transientFailure -> recordRetry(item, nextAttempts, deliveredJson)
            // Permanent failures are left out of the verdict: one dead device must not fail the others.
            delivered.isNotEmpty() -> mark(item, DeliveryStatus.SENT, nextAttempts, item.availableAt, deliveredJson)
            else -> mark(item, DeliveryStatus.DEAD, nextAttempts, item.availableAt, deliveredJson)
        }
        return outcome.invalidTokenIds
    }

    private fun sendToTargets(item: NotificationOutbox, targets: List<PushTarget>): SendOutcome {
        val data = objectMapper.readValue(item.dataJson, object : TypeReference<Map<String, String>>() {})
        val delivered = mutableSetOf<Long>()
        val invalid = mutableSetOf<Long>()
        var transientFailure = false

        targets.forEach { target ->
            val result = sender.send(
                token = target.token,
                title = item.title ?: "",
                body = item.body ?: "",
                data = data,
            )
            when {
                result.ok -> delivered += target.tokenId
                result.tokenInvalid -> invalid += target.tokenId
                !result.permanent -> transientFailure = true
            }
        }
        return SendOutcome(delivered, invalid, transientFailure)
    }

    private fun readDeliveredTokenIds(item: NotificationOutbox): Set<Long> {
        val json = item.deliveredTokenIds ?: return emptySet()
        return objectMapper.readValue(json, object : TypeReference<Set<Long>>() {})
    }

    private fun deactivateTokens(tokenIds: Set<Long>) {
        if (tokenIds.isEmpty()) return
        pushTokenRepository.deactivateAllByIdIn(tokenIds)
        log.info("Deactivated push tokens reported as unregistered: count={}", tokenIds.size)
    }

    private fun mark(
        item: NotificationOutbox,
        status: DeliveryStatus,
        attempts: Int,
        nextAt: Instant?,
        deliveredTokenIds: String?,
    ) {
        outboxRepository.updateResult(item.id, status, attempts, nextAt, deliveredTokenIds)
    }

    private data class SendOutcome(
        val deliveredTokenIds: Set<Long>,
        val invalidTokenIds: Set<Long>,
        val transientFailure: Boolean,
    )

    private companion object {
        const val CLAIM_LEASE_SECONDS = 600L
    }
}
