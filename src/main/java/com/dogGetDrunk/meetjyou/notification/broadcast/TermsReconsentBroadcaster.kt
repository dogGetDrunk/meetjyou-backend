package com.dogGetDrunk.meetjyou.notification.broadcast

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.TermsNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notification.BroadcastAlreadySentException
import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreferenceRepository
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplateFactory
import com.dogGetDrunk.meetjyou.terms.Terms
import com.dogGetDrunk.meetjyou.terms.TermsAgreementAction
import com.dogGetDrunk.meetjyou.terms.TermsRepository
import com.dogGetDrunk.meetjyou.terms.UserTermsRepository
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Locale
import java.util.UUID

/**
 * Writes the outbox rows of one terms reconsent broadcast in its own transaction.
 * Same structure as [NoticeBroadcaster]: terms row lock, dedup prefix check, all rows or none.
 */
// Constructor is the agreed broadcaster contract; clock keeps the eligibility check testable.
@Suppress("LongParameterList")
@Service
class TermsReconsentBroadcaster(
    private val termsRepository: TermsRepository,
    private val userTermsRepository: UserTermsRepository,
    private val preferenceRepository: UserNotificationPreferenceRepository,
    private val outboxRepository: NotificationOutboxRepository,
    private val templateFactory: NotificationTemplateFactory,
    private val objectMapper: ObjectMapper,
    // Used by the resend eligibility check (effectiveAt <= now); injectable for fixed-time tests.
    @Suppress("UnusedPrivateProperty") private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Broadcast sent when new terms are published. No eligibility check, so terms with a
     * future effectiveAt are still announced, as before. Returns the rows written and committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun broadcastOnPublish(termsUuid: UUID): Int {
        val terms = termsRepository.findByUuidForUpdate(termsUuid) ?: throw TermsNotFoundException(termsUuid.toString())
        val prefix = dedupPrefix(termsUuid)
        if (outboxRepository.existsByDedupKeyStartingWith(prefix)) {
            throw BroadcastAlreadySentException(prefix)
        }
        val rows = buildRows(terms, resolveRecipients(terms), prefix)
        outboxRepository.saveAll(rows)
        return rows.size
    }

    // Users whose latest agreement of this terms type is AGREED.
    private fun resolveRecipients(terms: Terms): List<User> {
        val candidates = userTermsRepository.findLatestByTermsType(terms.type, TermsAgreementAction.AGREED)
            .map { it.user }
            .filter { it.status == UserStatus.NORMAL && it.notified }
        val disabled = preferenceRepository.findDisabledUserIdsByType(NotificationType.TERMS_RECONSENT)
        return candidates.filter { it.id !in disabled }
    }

    private fun buildRows(terms: Terms, recipients: List<User>, prefix: String): List<NotificationOutbox> {
        val payload = NotificationPayload(
            type = NotificationType.TERMS_RECONSENT,
            bodyArgs = mapOf("displayText" to terms.displayText),
            data = mapOf("type" to NotificationType.TERMS_RECONSENT.name, "termsUuid" to terms.uuid.toString()),
        )
        val template = templateFactory.templateOf(NotificationType.TERMS_RECONSENT)
        val title = template.makeTitle(Locale.KOREAN, payload)
        val body = template.makeBody(Locale.KOREAN, payload)
        val dataJson = objectMapper.writeValueAsString(payload.data)
        return recipients.map { user ->
            NotificationOutbox(
                user = user,
                type = NotificationType.TERMS_RECONSENT,
                title = title,
                body = body,
                dataJson = dataJson,
                dedupKey = "$prefix${user.uuid}",
            )
        }
    }

    companion object {
        fun dedupPrefix(termsUuid: UUID): String = "terms_reconsent:$termsUuid:"
    }
}
