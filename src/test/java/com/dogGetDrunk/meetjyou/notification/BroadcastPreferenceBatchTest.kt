package com.dogGetDrunk.meetjyou.notification

import com.dogGetDrunk.meetjyou.notice.Notice
import com.dogGetDrunk.meetjyou.notice.NoticeRepository
import com.dogGetDrunk.meetjyou.notification.broadcast.NoticeBroadcaster
import com.dogGetDrunk.meetjyou.notification.broadcast.TermsReconsentBroadcaster
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.preference.NotificationPreferenceService
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreferenceRepository
import com.dogGetDrunk.meetjyou.notification.template.NoticeTemplate
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplateFactory
import com.dogGetDrunk.meetjyou.notification.template.TermsReconsentTemplate
import com.dogGetDrunk.meetjyou.terms.Terms
import com.dogGetDrunk.meetjyou.terms.TermsAgreementAction
import com.dogGetDrunk.meetjyou.terms.TermsRepository
import com.dogGetDrunk.meetjyou.terms.TermsType
import com.dogGetDrunk.meetjyou.terms.UserTerms
import com.dogGetDrunk.meetjyou.terms.UserTermsRepository
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.UserStatus
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class BroadcastPreferenceBatchTest {
    private val noticeRepository = mockk<NoticeRepository>()
    private val termsRepository = mockk<TermsRepository>()
    private val userRepository = mockk<UserRepository>()
    private val userTermsRepository = mockk<UserTermsRepository>()
    private val preferences = mockk<UserNotificationPreferenceRepository>()
    private val outbox = mockk<NotificationOutboxRepository>()
    private val templates = mockk<NotificationTemplateFactory>()
    private val mapper = ObjectMapper()
    private val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)
    private val notice = Notice("title", "content")
    private val terms = Terms(TermsType.TERMS_OF_SERVICE, "2", "서비스 이용약관", true, "key", "hash")
    private val saved = mutableListOf<NotificationOutbox>()

    init {
        every { noticeRepository.findByUuidForUpdate(notice.uuid) } returns notice
        every { termsRepository.findByUuidForUpdate(terms.uuid) } returns terms
        every { outbox.existsByDedupKeyStartingWith(any()) } returns false
        every { outbox.saveAll(any<Iterable<NotificationOutbox>>()) } answers {
            val rows = firstArg<Iterable<NotificationOutbox>>().toList()
            saved.addAll(rows)
            rows
        }
        every { templates.templateOf(NotificationType.NOTICE) } returns NoticeTemplate()
        every { templates.templateOf(NotificationType.TERMS_RECONSENT) } returns TermsReconsentTemplate()
        every { preferences.findDisabledUserIdsByType(any()) } returns emptySet()
    }

    // AT-003-1: repository calls stay constant as the eligible population grows.
    @Test
    fun `normal notice and terms query preferences once for one and ten candidates`() {
        for ((iteration, count) in listOf(1, 10).withIndex()) {
            val users = (1..count).map { user(it.toLong()) }
            every { userRepository.findAllByStatus(UserStatus.NORMAL) } returns users
            every { userTermsRepository.findLatestByTermsType(terms.type, TermsAgreementAction.AGREED) } returns
                users.map { UserTerms(terms, it) }
            val noticeCount = noticeBroadcaster().broadcast(notice.uuid, false)
            val termsCount = termsBroadcaster().broadcastOnPublish(terms.uuid)
            assertEquals(count, noticeCount)
            assertEquals(count, termsCount)
            verify(exactly = iteration + 1) { preferences.findDisabledUserIdsByType(NotificationType.NOTICE) }
            verify(exactly = iteration + 1) { preferences.findDisabledUserIdsByType(NotificationType.TERMS_RECONSENT) }
        }
        verify(exactly = 2) { preferences.findDisabledUserIdsByType(NotificationType.NOTICE) }
        verify(exactly = 2) { preferences.findDisabledUserIdsByType(NotificationType.TERMS_RECONSENT) }
        verify(exactly = 0) { preferences.findByUserAndNotificationType(any(), any()) }
        assertNoPreferenceServiceDependency()
    }

    @Test
    fun `normal notice and terms retain recipient rules and payload`() {
        val disabled = user(1)
        val otherTypeDisabled = user(2)
        val noRow = user(3)
        val enabled = user(4)
        val notNotified = user(5, notified = false)
        val deleted = user(6, status = UserStatus.DELETED)
        val purged = user(7, status = UserStatus.PURGED)
        val all = listOf(disabled, otherTypeDisabled, noRow, enabled, notNotified, deleted, purged)
        every { userRepository.findAllByStatus(UserStatus.NORMAL) } returns
            all.filter { it.status == UserStatus.NORMAL }
        every { userTermsRepository.findLatestByTermsType(terms.type, TermsAgreementAction.AGREED) } returns
            all.map { UserTerms(terms, it) }
        every { preferences.findDisabledUserIdsByType(NotificationType.NOTICE) } returns setOf(disabled.id)
        every { preferences.findDisabledUserIdsByType(NotificationType.TERMS_RECONSENT) } returns setOf(disabled.id)
        val expected = setOf(otherTypeDisabled.uuid, noRow.uuid, enabled.uuid)
        assertEquals(3, noticeBroadcaster().broadcast(notice.uuid, false))
        assertRows(expected, NOTICE_ROW, notice.uuid)
        saved.clear()
        assertEquals(3, termsBroadcaster().broadcastOnPublish(terms.uuid))
        assertRows(expected, TERMS_ROW, terms.uuid)
        verify(exactly = 1) { preferences.findDisabledUserIdsByType(NotificationType.NOTICE) }
        verify(exactly = 1) { preferences.findDisabledUserIdsByType(NotificationType.TERMS_RECONSENT) }
        verify(exactly = 0) { preferences.findByUserAndNotificationType(any(), any()) }
    }

    @Test
    fun `critical notice ignores category preference but excludes globally disabled users`() {
        for (count in listOf(1, 10)) {
            val candidates = (1..count).map { user(it.toLong()) }
            val notNotified = user(100, notified = false)
            every { userRepository.findAllByStatus(UserStatus.NORMAL) } returns candidates.plus(notNotified)
            every { preferences.findDisabledUserIdsByType(NotificationType.NOTICE) } returns
                setOf(candidates.first().id)
            assertEquals(count, noticeBroadcaster().broadcast(notice.uuid, true))
            assertRows(candidates.map { it.uuid }.toSet(), NOTICE_ROW, notice.uuid)
            saved.clear()
            verify(atMost = 1) { preferences.findDisabledUserIdsByType(any()) }
            verify(exactly = 0) { preferences.findByUserAndNotificationType(any(), any()) }
            clearMocks(preferences, answers = false, recordedCalls = true)
        }
        verify(exactly = 0) { preferences.findByUserAndNotificationType(any(), any()) }
    }

    private fun user(id: Long, notified: Boolean = true, status: UserStatus = UserStatus.NORMAL): User {
        val user = mockk<User>()
        every { user.id } returns id
        every { user.uuid } returns UUID(0, id)
        every { user.notified } returns notified
        every { user.status } returns status
        return user
    }

    private fun noticeBroadcaster() =
        NoticeBroadcaster(noticeRepository, userRepository, preferences, outbox, templates, mapper)

    private fun termsBroadcaster() = TermsReconsentBroadcaster(
        termsRepository,
        userTermsRepository,
        preferences,
        outbox,
        templates,
        mapper,
        clock,
    )

    private fun assertNoPreferenceServiceDependency() {
        for (type in listOf(NoticeBroadcaster::class.java, TermsReconsentBroadcaster::class.java)) {
            assertFalse(type.declaredFields.any { it.type == NotificationPreferenceService::class.java })
        }
    }

    private data class ExpectedRow(
        val type: NotificationType,
        val title: String,
        val body: String,
        val prefix: String,
    )

    private fun assertRows(users: Set<UUID>, expected: ExpectedRow, source: UUID) {
        assertEquals(users, saved.map { it.user.uuid }.toSet())
        assertEquals(users.size, saved.size)
        for (row in saved) {
            assertEquals(expected.type, row.type)
            assertEquals(expected.title, row.title)
            assertEquals(expected.body, row.body)
            val sourceKey = if (expected.type == NotificationType.NOTICE) "noticeUuid" else "termsUuid"
            val expectedData = mapOf("type" to expected.type.name, sourceKey to source.toString())
            assertEquals(expectedData, mapper.readValue(row.dataJson, Map::class.java))
            assertEquals("${expected.prefix}:$source:${row.user.uuid}", row.dedupKey)
        }
    }

    private companion object {
        private val NOTICE_ROW = ExpectedRow(NotificationType.NOTICE, "[공지사항]", "새로운 공지사항이 있어요.", "notice")
        private val TERMS_ROW = ExpectedRow(
            NotificationType.TERMS_RECONSENT,
            "[약관 변경 안내]",
            "서비스 이용약관 이(가) 변경되었어요. 확인 후 다시 동의해주세요.",
            "terms_reconsent",
        )
    }
}
