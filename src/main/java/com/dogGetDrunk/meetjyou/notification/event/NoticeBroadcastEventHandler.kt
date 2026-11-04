package com.dogGetDrunk.meetjyou.notification.event

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NoticeNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notification.BroadcastAlreadySentException
import com.dogGetDrunk.meetjyou.notification.broadcast.NoticeBroadcaster
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// No @Transactional here on purpose: the broadcaster runs and commits in its own transaction, so
// a commit-time failure (e.g. UnexpectedRollbackException) surfaces from the proxy call and is
// caught below instead of escaping into the already committed notice request.
@Service
class NoticeBroadcastEventHandler(
    private val noticeBroadcaster: NoticeBroadcaster,
    private val discordAlertService: DiscordAlertService,
) {
    private val log = LoggerFactory.getLogger(NoticeBroadcastEventHandler::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // Skip cases are expected outcomes, logged at INFO without the exception on purpose.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun on(event: NoticeBroadcastEvent) {
        val uuid = event.noticeUuid
        try {
            val count = noticeBroadcaster.broadcast(uuid, event.critical)
            log.info("Notice broadcast queued: noticeUuid={}, recipientCount={}", uuid, count)
        } catch (e: BroadcastAlreadySentException) {
            log.info("Notice broadcast skipped, already sent: noticeUuid={}", uuid)
        } catch (e: NoticeNotFoundException) {
            log.info("Notice broadcast skipped, notice not found: noticeUuid={}", uuid)
        } catch (e: Exception) {
            log.error("Notice broadcast failed, nothing queued: noticeUuid={}", uuid, e)
            discordAlertService.sendOperationalAlert(
                source = "$ALERT_SOURCE:$uuid",
                exceptionClass = e.javaClass.simpleName,
                summary = "Notice broadcast failed, 0 sent. Resend with POST /api/v1/notices/$uuid/broadcast",
                detail = e.message,
            )
        }
    }

    private companion object {
        const val ALERT_SOURCE = "NoticeBroadcast"
    }
}
