package com.dogGetDrunk.meetjyou.notification.event

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.TermsNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notification.BroadcastAlreadySentException
import com.dogGetDrunk.meetjyou.notification.broadcast.TermsReconsentBroadcaster
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// Not transactional for the same reason as NoticeBroadcastEventHandler.
@Service
class TermsReconsentEventHandler(
    private val termsReconsentBroadcaster: TermsReconsentBroadcaster,
    private val discordAlertService: DiscordAlertService,
) {
    private val log = LoggerFactory.getLogger(TermsReconsentEventHandler::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // Skip cases are expected outcomes, logged at INFO without the exception on purpose.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun on(event: TermsReconsentEvent) {
        val uuid = event.termsUuid
        try {
            val count = termsReconsentBroadcaster.broadcastOnPublish(uuid)
            log.info("Terms reconsent notification queued: termsUuid={}, recipientCount={}", uuid, count)
        } catch (e: BroadcastAlreadySentException) {
            log.info("Terms reconsent broadcast skipped, already sent: termsUuid={}", uuid)
        } catch (e: TermsNotFoundException) {
            log.info("Terms reconsent broadcast skipped, terms not found: termsUuid={}", uuid)
        } catch (e: Exception) {
            log.error("Terms reconsent broadcast failed, nothing queued: termsUuid={}", uuid, e)
            val resend = "POST /api/v1/terms/$uuid/reconsent-broadcast"
            discordAlertService.sendOperationalAlert(
                source = "$ALERT_SOURCE:$uuid",
                exceptionClass = e.javaClass.simpleName,
                summary = "Terms reconsent broadcast failed, 0 sent. Resend with $resend",
                detail = e.message,
            )
        }
    }

    private companion object {
        const val ALERT_SOURCE = "TermsReconsentBroadcast"
    }
}
