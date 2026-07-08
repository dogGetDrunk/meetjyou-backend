package com.dogGetDrunk.meetjyou.notification.event

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.preference.NotificationPreferenceService
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplateFactory
import com.dogGetDrunk.meetjyou.terms.TermsAgreementAction
import com.dogGetDrunk.meetjyou.terms.UserTermsRepository
import com.dogGetDrunk.meetjyou.user.UserStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.Locale

@Service
class TermsReconsentEventHandler(
    private val userTermsRepository: UserTermsRepository,
    private val preferenceService: NotificationPreferenceService,
    private val templateFactory: NotificationTemplateFactory,
    private val outboxRepository: NotificationOutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(TermsReconsentEventHandler::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: TermsReconsentEvent) {
        val payload = NotificationPayload(
            type = NotificationType.TERMS_RECONSENT,
            bodyArgs = mapOf("displayText" to event.displayText),
            data = mapOf(
                "type" to "TERMS_RECONSENT",
                "termsUuid" to event.termsUuid.toString(),
            ),
        )
        val template = templateFactory.templateOf(NotificationType.TERMS_RECONSENT)
        val title = template.makeTitle(Locale.KOREAN, payload)
        val body = template.makeBody(Locale.KOREAN, payload)
        val dataJson = objectMapper.writeValueAsString(payload.data)

        val outboxRows = userTermsRepository.findLatestByTermsType(event.termsType, TermsAgreementAction.AGREED)
            .map { it.user }
            .filter { user -> user.status == UserStatus.NORMAL }
            .filter { user -> preferenceService.isEnabled(user, NotificationType.TERMS_RECONSENT) }
            .map { user ->
                NotificationOutbox(
                    user = user,
                    type = NotificationType.TERMS_RECONSENT,
                    title = title,
                    body = body,
                    dataJson = dataJson,
                    dedupKey = "terms_reconsent:${event.termsUuid}:${user.uuid}",
                )
            }

        outboxRepository.saveAll(outboxRows)
        log.info(
            "Terms reconsent notification queued: termsUuid={}, recipientCount={}",
            event.termsUuid,
            outboxRows.size,
        )
    }
}
