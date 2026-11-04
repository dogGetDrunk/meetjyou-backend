package com.dogGetDrunk.meetjyou.post

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.view.PostViewCount
import com.dogGetDrunk.meetjyou.post.view.PostViewCountRepository
import com.dogGetDrunk.meetjyou.post.view.PostViewLogId
import com.dogGetDrunk.meetjyou.post.view.PostViewLogRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.reset
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(
    properties = [
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=300",
    ],
)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class PostDetailConnectionIntegrationTest {
    @Autowired private lateinit var controller: PostController
    @Autowired private lateinit var service: PostService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var parties: PartyRepository
    @Autowired private lateinit var posts: PostRepository
    @Autowired private lateinit var plans: PlanRepository
    @Autowired private lateinit var logs: PostViewLogRepository
    @Autowired private lateinit var transaction: TransactionTemplate
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var dataSource: HikariDataSource
    @SpyBean private lateinit var counts: PostViewCountRepository

    private data class Fixture(
        val post: UUID, val postId: Long, val user: UUID, val userId: Long, val party: UUID,
        val plan: UUID, val nickname: String,
    )

    private fun fixture(): Fixture = transaction.execute {
        val suffix = UUID.randomUUID().toString()
        val user = users.save(User(null, suffix.take(10), AuthProvider.KAKAO, suffix))
        val now = Instant.now()
        val party = parties.save(Party(now, now.plusSeconds(3600), "Seoul", 1, 4, "Trip"))
        val plan = plans.save(Plan("Plan", now, now.plusSeconds(3600), "Seoul", 37.5, 127.0, owner = user))
        val post = Post(party, false, "Title", "Content", now, now.plusSeconds(3600), "Seoul", 4)
        post.author = user
        post.plan = plan
        post.isPlanPublic = true
        posts.saveAndFlush(post)
        counts.saveAndFlush(PostViewCount(post.id, 7))
        Fixture(post.uuid, post.id, user.uuid, user.id, party.uuid, plan.uuid, user.nickname)
    } ?: error("Fixture transaction returned null")

    private fun requestAs(uuid: UUID) {
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest()))
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(CustomUserPrincipal(uuid), null, emptyList())
    }

    private fun state(f: Fixture): Pair<Long, Int> = transaction.execute {
        val views = counts.findById(f.postId).orElseThrow().views
        views to logs.findAll().count { it.id.postId == f.postId }
    } ?: error("State transaction returned null")

    private fun assertDto(f: Fixture, views: Long, response: com.dogGetDrunk.meetjyou.post.dto.GetPostResponse) {
        assertEquals(f.post, response.uuid)
        assertEquals("Title", response.title)
        assertEquals("Content", response.content)
        assertEquals(f.user, response.authorUuid)
        assertEquals(f.user, response.author.uuid)
        assertEquals(f.nickname, response.author.nickname)
        assertEquals(f.party, response.partyUuid)
        assertEquals(1, response.joined)
        assertEquals(f.plan, response.planUuid)
        assertEquals(true, response.isPlanPublic)
        assertEquals(f.plan, response.plan?.uuid)
        assertEquals("Plan", response.plan?.title)
        assertEquals(f.user, response.plan?.userUuid)
        assertEquals(views, response.views)
    }

    @AfterEach
    fun clearRequest() {
        reset(counts)
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `AT-002-1 commits view with one connection`() {
        val f = fixture()
        assertEquals(1, dataSource.maximumPoolSize)
        requestAs(f.user)
        assertDto(f, 7, controller.getPostByUuid(f.post))
        assertEquals(8L to 1, state(f))
    }

    @Test
    fun `AT-002-2 duplicate and missing user preserve count`() {
        val f = fixture()
        val firstRequestAt = Instant.now()
        requestAs(f.user)
        assertDto(f, 7, controller.getPostByUuid(f.post))
        assertEquals(8L to 1, state(f))
        val viewedAt = viewedAt(f)
        requestAs(f.user)
        assertDto(f, 8, controller.getPostByUuid(f.post))
        assertTrue(Duration.between(firstRequestAt, Instant.now()) < Duration.ofMinutes(10))
        assertEquals(8L to 1, state(f))
        assertEquals(viewedAt, viewedAt(f))
        val other = fixture()
        requestAs(UUID.randomUUID())
        assertDto(other, 7, service.getPostByUuid(other.post))
        assertEquals(7L to 0, state(other))
    }

    @Test
    fun `AT-002-3 write failure rolls back without breaking response`() {
        val f = fixture()
        val injected = AtomicBoolean(false)
        val appender = ListAppender<ILoggingEvent>()
        val logger = LoggerFactory.getLogger(PostService::class.java) as Logger
        appender.start()
        logger.addAppender(appender)
        try {
            failAfterViewWrites(f, injected)
            requestAs(f.user)
            assertDto(f, 7, controller.getPostByUuid(f.post))
            assertEquals(7L to 0, state(f))
            assertTrue(injected.get())
            assertTrue(
                appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("Failed to increment") },
            )
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    // Lets the real view writes run and flush, checks they reached the DB, then throws
    // so the rollback of those writes can be asserted.
    // The spy wraps a Spring Data interface proxy, so callRealMethod() has no body to call;
    // the spy's default answer delegates to the real repository bean instead.
    private fun failAfterViewWrites(f: Fixture, injected: AtomicBoolean) {
        val realCall = mockingDetails(counts).mockCreationSettings.defaultAnswer
        doAnswer { invocation ->
            realCall.answer(invocation)
            entityManager.flush()
            assertEquals(8L, countByPost("select views from post_view_counts where post_id = :id", f.postId))
            assertEquals(1L, countByPost("select count(*) from post_view_logs where post_id = :id", f.postId))
            injected.set(true)
            error("injected after view writes")
        }.`when`(counts).upsertIncrement(anyLong())
    }

    private fun countByPost(sql: String, postId: Long): Long =
        (entityManager.createNativeQuery(sql).setParameter("id", postId).singleResult as Number).toLong()

    private fun viewedAt(f: Fixture) =
        transaction.execute { logs.findById(PostViewLogId(f.userId, f.postId)).orElseThrow().viewedAt }
}
