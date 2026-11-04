package com.dogGetDrunk.meetjyou.notification.broadcast

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NoticeNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notification.BroadcastAlreadySentException
import com.dogGetDrunk.meetjyou.notice.Notice
import com.dogGetDrunk.meetjyou.notice.NoticeRepository
import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreferenceRepository
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplateFactory
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.UserStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID

/**
 * Writes the outbox rows of one notice broadcast in its own transaction.
 * The notice row lock serializes concurrent broadcasts of the same notice, so the
 * dedup prefix check and the inserts cannot interleave; any failure rolls back every row.
 */
@Service
class NoticeBroadcaster(
    private val noticeRepository: NoticeRepository,
    private val userRepository: UserRepository,
    private val preferenceRepository: UserNotificationPreferenceRepository,
    private val outboxRepository: NotificationOutboxRepository,
    private val templateFactory: NotificationTemplateFactory,
    private val objectMapper: ObjectMapper,
) {
    /** Returns the number of outbox rows written and committed by this call. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun broadcast(noticeUuid: UUID, critical: Boolean): Int {
        val notice = noticeRepository.findByUuidForUpdate(noticeUuid) ?: throw NoticeNotFoundException(noticeUuid)
        val prefix = dedupPrefix(noticeUuid)
        if (outboxRepository.existsByDedupKeyStartingWith(prefix)) {
            throw BroadcastAlreadySentException(prefix)
        }
        val rows = buildRows(notice, resolveRecipients(critical), prefix)
        outboxRepository.saveAll(rows)
        return rows.size
    }

    // Critical notices ignore the category setting, but the global switch (notified) still applies.
    // The lookup still runs once for them, so every broadcast costs exactly one preference query.
    private fun resolveRecipients(critical: Boolean): List<User> {
        val candidates = userRepository.findAllByStatus(UserStatus.NORMAL).filter { it.notified }
        val disabled = preferenceRepository.findDisabledUserIdsByType(NotificationType.NOTICE)
        return if (critical) candidates else candidates.filter { it.id !in disabled }
    }

    private fun buildRows(notice: Notice, recipients: List<User>, prefix: String): List<NotificationOutbox> {
        val payload = NotificationPayload(
            type = NotificationType.NOTICE,
            bodyArgs = mapOf("title" to notice.title, "content" to notice.body),
            data = mapOf("type" to NotificationType.NOTICE.name, "noticeUuid" to notice.uuid.toString()),
        )
        val template = templateFactory.templateOf(NotificationType.NOTICE)
        val title = template.makeTitle(Locale.KOREAN, payload)
        val body = template.makeBody(Locale.KOREAN, payload)
        val dataJson = objectMapper.writeValueAsString(payload.data)
        return recipients.map { user ->
            NotificationOutbox(
                user = user,
                type = NotificationType.NOTICE,
                title = title,
                body = body,
                dataJson = dataJson,
                dedupKey = "$prefix${user.uuid}",
            )
        }
    }

    companion object {
        fun dedupPrefix(noticeUuid: UUID): String = "notice:$noticeUuid:"
    }
}
