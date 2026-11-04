package com.dogGetDrunk.meetjyou.notification

import com.dogGetDrunk.meetjyou.notice.Notice
import com.dogGetDrunk.meetjyou.notification.event.NoticeBroadcastEvent
import com.dogGetDrunk.meetjyou.notification.event.TermsReconsentEvent
import com.dogGetDrunk.meetjyou.notification.dispatcher.NotificationDispatcher
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutbox
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreference
import com.dogGetDrunk.meetjyou.terms.Terms
import com.dogGetDrunk.meetjyou.terms.TermsStatus
import com.dogGetDrunk.meetjyou.terms.TermsType
import com.dogGetDrunk.meetjyou.terms.UserTerms
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import jakarta.persistence.EntityManager
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest
@ActiveProfiles("test")
@Import(BroadcastPreferenceBatchIntegrationTest.SqlConfig::class)
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
    NotificationDispatcher::class,
)
class BroadcastPreferenceBatchIntegrationTest {
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var publisher: ApplicationEventPublisher
    @Autowired private lateinit var mapper: ObjectMapper

    @Test
    fun `AT-003-2 notice and terms select preferences once for one and ten candidates`() {
        listOf(1, 10).forEach { size ->
            runScenario(size, false, false)
            runScenario(size, true, false)
        }
    }

    @Test
    fun `AT-003-2 critical notice ignores category preferences but respects global and status`() {
        runScenario(10, false, true)
    }

    private fun runScenario(size: Int, terms: Boolean, critical: Boolean) {
        val fixture = createFixture(size, terms)
        SqlCollector.statements.clear()
        tx {
            if (terms) {
                publisher.publishEvent(TermsReconsentEvent(fixture.source, TermsType.TERMS_OF_SERVICE, DISPLAY))
            } else {
                publisher.publishEvent(NoticeBroadcastEvent(fixture.source, NOTICE_TITLE, NOTICE_BODY, critical))
            }
        }
        val selects = preferenceSelectCount()
        if (critical) assertTrue(selects <= 1) else assertEquals(1, selects)
        val expected = fixture.users.filterIndexed { index, _ ->
            size == 1 || (index != 4 && index != 5 && (critical || index != 0))
        }.toSet()
        assertOutboxRows(fixture, terms, expected)
        cleanup(fixture)
    }

    private fun preferenceSelectCount(): Int = SqlCollector.statements.count {
        it.trimStart().startsWith("select", ignoreCase = true) &&
            it.contains("user_notification_preference", ignoreCase = true)
    }

    private fun assertOutboxRows(fixture: Fixture, terms: Boolean, expected: Set<UUID>) {
        tx {
            val prefix = if (terms) "terms_reconsent:${fixture.source}:" else "notice:${fixture.source}:"
            val rows = entityManager.createQuery(
                "select o from NotificationOutbox o join fetch o.user where o.dedupKey like :prefix",
                NotificationOutbox::class.java,
            ).setParameter("prefix", "$prefix%").resultList
            assertEquals(expected, rows.map { it.user.uuid }.toSet())
            assertEquals(expected.size, rows.size)
            rows.forEach { row -> assertRow(row, fixture, terms, prefix) }
        }
    }

    private fun assertRow(row: NotificationOutbox, fixture: Fixture, terms: Boolean, prefix: String) {
        assertEquals(if (terms) NotificationType.TERMS_RECONSENT else NotificationType.NOTICE, row.type)
        assertEquals(if (terms) "[약관 변경 안내]" else "[공지사항]", row.title)
        val expectedBody = if (terms) "$DISPLAY 이(가) 변경되었어요. 확인 후 다시 동의해주세요." else "새로운 공지사항이 있어요."
        assertEquals(expectedBody, row.body)
        val expectedData = if (terms) {
            "{\"type\":\"TERMS_RECONSENT\",\"termsUuid\":\"${fixture.source}\"}"
        } else {
            "{\"type\":\"NOTICE\",\"noticeUuid\":\"${fixture.source}\"}"
        }
        assertEquals(mapper.readTree(expectedData), mapper.readTree(row.dataJson))
        assertEquals("$prefix${row.user.uuid}", row.dedupKey)
    }

    private fun createFixture(size: Int, terms: Boolean): Fixture {
        lateinit var result: Fixture
        tx {
            val old = Terms(TermsType.TERMS_OF_SERVICE, "1", DISPLAY, true, "old", "old", TermsStatus.INACTIVE)
            val current = Terms(TermsType.TERMS_OF_SERVICE, "2", DISPLAY, true, "new", "new")
            val notice = Notice(NOTICE_TITLE, NOTICE_BODY)
            entityManager.persist(old)
            entityManager.persist(current)
            entityManager.persist(notice)
            val users = (0 until size).map { index -> persistCandidate(index, size, terms, old) }
            entityManager.flush()
            result = Fixture(if (terms) current.uuid else notice.uuid, users, notice.uuid, old.id, current.id)
            entityManager.clear()
        }
        return result
    }

    private fun persistCandidate(index: Int, size: Int, terms: Boolean, old: Terms): UUID {
        val key = UUID.randomUUID().toString()
        val user = User(null, key.take(10), AuthProvider.KAKAO, key)
        if (size > 1 && index == 4) user.notified = false
        if (size > 1 && index == 5) user.withdraw(Instant.now())
        entityManager.persist(user)
        entityManager.persist(UserTerms(old, user))
        if (size > 1 && index < 3) {
            val type = when (index) {
                0 -> if (terms) NotificationType.TERMS_RECONSENT else NotificationType.NOTICE
                1 -> if (terms) NotificationType.NOTICE else NotificationType.TERMS_RECONSENT
                else -> if (terms) NotificationType.TERMS_RECONSENT else NotificationType.NOTICE
            }
            entityManager.persist(UserNotificationPreference(user, type, index == 2))
        }
        return user.uuid
    }

    private fun cleanup(fixture: Fixture) {
        tx {
            bulkDelete("delete from NotificationOutbox o where o.user.uuid in :ids", "ids", fixture.users)
            bulkDelete("delete from UserNotificationPreference p where p.user.uuid in :ids", "ids", fixture.users)
            bulkDelete("delete from UserTerms u where u.user.uuid in :ids", "ids", fixture.users)
            bulkDelete("delete from User u where u.uuid in :ids", "ids", fixture.users)
            bulkDelete("delete from Notice n where n.uuid = :id", "id", fixture.notice)
            bulkDelete("delete from Terms t where t.id in :ids", "ids", listOf(fixture.oldTerms, fixture.newTerms))
        }
    }

    private fun bulkDelete(jpql: String, name: String, value: Any) {
        entityManager.createQuery(jpql).setParameter(name, value).executeUpdate()
    }

    private fun tx(block: () -> Unit) = TransactionTemplate(transactionManager).executeWithoutResult { block() }

    private data class Fixture(
        val source: UUID,
        val users: List<UUID>,
        val notice: UUID,
        val oldTerms: Long,
        val newTerms: Long,
    )

    @TestConfiguration
    class SqlConfig {
        @Bean
        fun statementInspectorCustomizer(): HibernatePropertiesCustomizer = HibernatePropertiesCustomizer {
            it["hibernate.session_factory.statement_inspector"] = SqlCollector
        }
    }

    object SqlCollector : StatementInspector {
        val statements = CopyOnWriteArrayList<String>()
        override fun inspect(sql: String): String {
            statements.add(sql)
            return sql
        }
    }

    companion object {
        private const val DISPLAY = "서비스 이용약관"
        private const val NOTICE_TITLE = "공지 제목"
        private const val NOTICE_BODY = "공지 내용"
    }
}
