package com.dogGetDrunk.meetjyou.notification

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NoticeNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.TermsNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notification.BroadcastAlreadySentException
import com.dogGetDrunk.meetjyou.notice.Notice
import com.dogGetDrunk.meetjyou.notice.NoticeRepository
import com.dogGetDrunk.meetjyou.notification.broadcast.NoticeBroadcaster
import com.dogGetDrunk.meetjyou.notification.broadcast.TermsReconsentBroadcaster
import com.dogGetDrunk.meetjyou.notification.event.NoticeBroadcastEvent
import com.dogGetDrunk.meetjyou.notification.event.NoticeBroadcastEventHandler
import com.dogGetDrunk.meetjyou.notification.event.TermsReconsentEvent
import com.dogGetDrunk.meetjyou.notification.event.TermsReconsentEventHandler
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreferenceRepository
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplateFactory
import com.dogGetDrunk.meetjyou.terms.Terms
import com.dogGetDrunk.meetjyou.terms.TermsRepository
import com.dogGetDrunk.meetjyou.terms.TermsType
import com.dogGetDrunk.meetjyou.terms.UserTermsRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.transaction.UnexpectedRollbackException
import java.util.UUID

// AT-004-3
class BroadcastAlreadySentTest {
    private val uuid = UUID.randomUUID()
    private val noticeEvent = NoticeBroadcastEvent(uuid, "title", "body", false)
    private val termsEvent = TermsReconsentEvent(uuid, TermsType.TERMS_OF_SERVICE, "terms")
    private val alert = mockk<DiscordAlertService>(relaxed = true)
    private val outbox = mockk<NotificationOutboxRepository>(relaxed = true)
    private val preference = mockk<UserNotificationPreferenceRepository>(relaxed = true)
    private val templates = mockk<NotificationTemplateFactory>(relaxed = true)
    private val mapper = ObjectMapper()

    @Test
    fun `notice already sent is skipped through real broadcaster on repeated events`() {
        val notices = mockk<NoticeRepository>()
        every { notices.findByUuidForUpdate(uuid) } returns mockk<Notice>()
        every { outbox.existsByDedupKeyStartingWith("notice:$uuid:") } returns true
        val broadcaster = NoticeBroadcaster(notices, mockk<UserRepository>(), preference, outbox, templates, mapper)
        val handler = NoticeBroadcastEventHandler(broadcaster, alert)
        withLogs(NoticeBroadcastEventHandler::class.java) { logs ->
            repeat(2) { handler.on(noticeEvent) }
            assertSkipLogs(logs, 2)
        }
        verify(exactly = 2) { notices.findByUuidForUpdate(uuid) }
        verify(exactly = 2) { outbox.existsByDedupKeyStartingWith("notice:$uuid:") }
        verify(exactly = 0) { outbox.saveAll(any<Iterable<NotificationOutbox>>()) }
        verify(exactly = 0) { alert.sendOperationalAlert(any(), any(), any(), any()) }
    }

    @Test
    fun `terms already sent is skipped through real broadcaster on repeated events`() {
        val terms = mockk<TermsRepository>()
        every { terms.findByUuidForUpdate(uuid) } returns mockk<Terms>()
        every { outbox.existsByDedupKeyStartingWith("terms_reconsent:$uuid:") } returns true
        val broadcaster = TermsReconsentBroadcaster(
            terms,
            mockk<UserTermsRepository>(),
            preference,
            outbox,
            templates,
            mapper,
        )
        val handler = TermsReconsentEventHandler(broadcaster, alert)
        withLogs(TermsReconsentEventHandler::class.java) { logs ->
            repeat(2) { handler.on(termsEvent) }
            assertSkipLogs(logs, 2)
        }
        verify(exactly = 2) { terms.findByUuidForUpdate(uuid) }
        verify(exactly = 2) { outbox.existsByDedupKeyStartingWith("terms_reconsent:$uuid:") }
        verify(exactly = 0) { outbox.saveAll(any<Iterable<NotificationOutbox>>()) }
        verify(exactly = 0) { alert.sendOperationalAlert(any(), any(), any(), any()) }
    }

    @Test
    fun `deleted notice is skipped without writing outbox or alert`() {
        val notices = mockk<NoticeRepository>()
        every { notices.findByUuidForUpdate(uuid) } returns null
        val broadcaster = NoticeBroadcaster(notices, mockk<UserRepository>(), preference, outbox, templates, mapper)
        val handler = NoticeBroadcastEventHandler(broadcaster, alert)
        withLogs(NoticeBroadcastEventHandler::class.java) { logs ->
            handler.on(noticeEvent)
            assertSkipLogs(logs, 1)
        }
        verify(exactly = 0) { outbox.existsByDedupKeyStartingWith(any()) }
        verify(exactly = 0) { outbox.saveAll(any<Iterable<NotificationOutbox>>()) }
        verify(exactly = 0) { alert.sendOperationalAlert(any(), any(), any(), any()) }
    }

    @Test
    fun `notice handler distinguishes skipped exceptions from commit failure`() {
        val broadcaster = mockk<NoticeBroadcaster>()
        val handler = NoticeBroadcastEventHandler(broadcaster, alert)
        listOf(BroadcastAlreadySentException("notice:$uuid:"), NoticeNotFoundException(uuid)).forEach { failure ->
            every { broadcaster.broadcast(uuid, false) } throws failure
            handler.on(noticeEvent)
        }
        verify(exactly = 0) { alert.sendOperationalAlert(any(), any(), any(), any()) }
        every { broadcaster.broadcast(uuid, false) } throws UnexpectedRollbackException("commit failed")
        withLogs(NoticeBroadcastEventHandler::class.java) { logs ->
            handler.on(noticeEvent)
            assertEquals(1, logs.list.count { it.level == Level.ERROR })
        }
        verify(exactly = 1) {
            alert.sendOperationalAlert("NoticeBroadcast:$uuid", "UnexpectedRollbackException", any(), any())
        }
    }

    @Test
    fun `terms handler distinguishes skipped exceptions from commit failure`() {
        val broadcaster = mockk<TermsReconsentBroadcaster>()
        val handler = TermsReconsentEventHandler(broadcaster, alert)
        val skipped = listOf(
            BroadcastAlreadySentException("terms_reconsent:$uuid:"),
            TermsNotFoundException(uuid.toString()),
        )
        skipped.forEach { failure ->
            every { broadcaster.broadcastOnPublish(uuid) } throws failure
            handler.on(termsEvent)
        }
        verify(exactly = 0) { alert.sendOperationalAlert(any(), any(), any(), any()) }
        every { broadcaster.broadcastOnPublish(uuid) } throws UnexpectedRollbackException("commit failed")
        withLogs(TermsReconsentEventHandler::class.java) { logs ->
            handler.on(termsEvent)
            assertEquals(1, logs.list.count { it.level == Level.ERROR })
        }
        verify(exactly = 1) {
            alert.sendOperationalAlert("TermsReconsentBroadcast:$uuid", "UnexpectedRollbackException", any(), any())
        }
    }

    private fun withLogs(type: Class<*>, block: (ListAppender<ILoggingEvent>) -> Unit) {
        val logger = LoggerFactory.getLogger(type) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            block(appender)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun assertSkipLogs(logs: ListAppender<ILoggingEvent>, expected: Int) {
        assertEquals(expected, logs.list.count { it.level == Level.INFO })
        val infoLogs = logs.list.filter { it.level == Level.INFO }
        assertTrue(infoLogs.all { it.formattedMessage.contains("skip", ignoreCase = true) })
        assertEquals(0, logs.list.count { it.level == Level.ERROR })
    }
}
