package com.dogGetDrunk.meetjyou.notification.event

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.preference.NotificationPreferenceService
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplateFactory
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.UserStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.Locale

@Service
class NoticeBroadcastEventHandler(
    private val userRepository: UserRepository,
    private val preferenceService: NotificationPreferenceService,
    private val templateFactory: NotificationTemplateFactory,
    private val outboxRepository: NotificationOutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(NoticeBroadcastEventHandler::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    fun on(event: NoticeBroadcastEvent) {
        val payload = NotificationPayload(
            type = NotificationType.NOTICE,
            bodyArgs = mapOf("title" to event.noticeTitle, "content" to event.noticeBody),
            data = mapOf(
                "type" to "NOTICE",
                "noticeUuid" to event.noticeUuid.toString(),
            ),
        )
        val template = templateFactory.templateOf(NotificationType.NOTICE)
        val title = template.makeTitle(Locale.KOREAN, payload)
        val body = template.makeBody(Locale.KOREAN, payload)
        val dataJson = objectMapper.writeValueAsString(payload.data)

        val outboxRows = userRepository.findAllByStatus(UserStatus.NORMAL)
            .filter { user -> event.critical || preferenceService.isEnabled(user, NotificationType.NOTICE) }
            .map { user ->
                NotificationOutbox(
                    user = user,
                    type = NotificationType.NOTICE,
                    title = title,
                    body = body,
                    dataJson = dataJson,
                    dedupKey = "notice:${event.noticeUuid}:${user.uuid}",
                )
            }

        outboxRepository.saveAll(outboxRows)
        log.info("Notice broadcast queued: noticeUuid={}, recipientCount={}", event.noticeUuid, outboxRows.size)
    }
}
