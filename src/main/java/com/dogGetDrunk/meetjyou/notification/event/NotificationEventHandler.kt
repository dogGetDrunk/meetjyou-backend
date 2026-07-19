package com.dogGetDrunk.meetjyou.notification.event

import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.preference.NotificationPreferenceService
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplateFactory
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class NotificationEventHandler(
    private val templateFactory: NotificationTemplateFactory,
    private val outboxRepository: NotificationOutboxRepository,
    private val userRepository: UserRepository,
    private val preferenceService: NotificationPreferenceService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(NotificationEventHandler::class.java)

    // REQUIRES_NEW is mandatory in an AFTER_COMMIT listener: without it the repository
    // save joins the already-committed transaction and the outbox row is never flushed.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: NotificationEvent) {
        val user = userRepository.findByUuid(event.userUuid) ?: run {
            log.warn("User not found for UUID: {}, skipping notification.", event.userUuid)
            return
        }

        if (!preferenceService.isEnabled(user, event.payload.type)) {
            log.info("Notification suppressed: type={}, userId={}", event.payload.type, user.id)
            return
        }

        val dedupKey = event.payload.dedupKey
        if (dedupKey != null && outboxRepository.existsByDedupKey(dedupKey)) {
            log.info("Duplicate notification suppressed: dedupKey={}, userId={}", dedupKey, user.id)
            return
        }

        val template = templateFactory.templateOf(event.payload.type)
        val title = template.makeTitle(event.preferredLocale, event.payload)
        val body = template.makeBody(event.preferredLocale, event.payload)

        val outbox = NotificationOutbox(
            user = user,
            type = NotificationType.valueOf(event.payload.type.name),
            title = title,
            body = body,
            dataJson = objectMapper.writeValueAsString(event.payload.data),
            dedupKey = event.payload.dedupKey,
        )
        outboxRepository.save(outbox)

        log.info(
            "Notification queued: type={}, userUuid={}, userId={}, outboxId={}",
            event.payload.type, event.userUuid, user.id, outbox.id
        )
    }

}
