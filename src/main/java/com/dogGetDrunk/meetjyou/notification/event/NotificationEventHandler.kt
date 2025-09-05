package com.dogGetDrunk.meetjyou.notification.event

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplateFactory
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class NotificationEventHandler(
    private val templateFactory: NotificationTemplateFactory,
    private val outboxRepository: NotificationOutboxRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(NotificationEventHandler::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: NotificationEvent) {
        // 1. UUID로 내부 사용자 조회
        val user = userRepository.findByUuid(event.userUuid) ?: run {
            log.warn("User not found for UUID: ${event.userUuid}, skipping notification.")
            throw UserNotFoundException(event.userUuid)
        }

        // 2. 템플릿으로 title/body 생성
        val template = templateFactory.templateOf(event.payload.type)
        val title = template.makeTitle(event.preferredLocale, event.payload)
        val body = template.makeBody(event.preferredLocale, event.payload)

        // 3. Outbox에 저장
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
            "Queued notification: type={}, userUuid={}, userId={}, outboxId={}",
            event.payload.type, event.userUuid, user.id, outbox.id
        )
    }

}
