package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.post.PostService
import com.dogGetDrunk.meetjyou.post.PostStatus
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.support.firstLostDeadlock
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Issue #165: PostService.updatePost (no lock, post row written before party row at flush) and
 * PartyService.completeParty (party row locked first, post row written at flush) take the same
 * two row locks in opposite order. H2 has no InnoDB deadlock detection, so this needs MySQL.
 *
 * The interleaving is forced: completion takes the party lock and stays open, the edit runs until
 * it waits on the party row, then completion commits and needs the post row. The deadlock is
 * accepted (rare, unnatural overlap) and handled as a 409 instead of restructuring lock order:
 * this pins that the loser fails with the mapped exception type and leaves no partial state.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostUpdatePartyCompleteDeadlockDockerTest : BehaviorSpec() {

    @SpringBootConfiguration
    @EntityScan(basePackages = [BASE_PACKAGE])
    @EnableJpaRepositories(basePackages = [BASE_PACKAGE])
    class Config

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var partyRepository: PartyRepository
    @Autowired private lateinit var postRepository: PostRepository
    @Autowired private lateinit var planRepository: PlanRepository
    @Autowired private lateinit var markerRepository: MarkerRepository
    @Autowired private lateinit var chatRoomRepository: ChatRoomRepository
    @Autowired private lateinit var userPartyRepository: UserPartyRepository
    @Autowired private lateinit var compPreferenceRepository: CompPreferenceRepository
    @Autowired private lateinit var preferenceRepository: PreferenceRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val log = LoggerFactory.getLogger(javaClass)
    private val currentUserUuid = ThreadLocal<UUID>()
    private val currentUserProvider = mockk<CurrentUserProvider>()
    private val tx by lazy { TransactionTemplate(transactionManager) }

    private val partyService by lazy {
        PartyService(
            partyRepository, postRepository, planRepository, markerRepository, chatRoomRepository,
            mockk(relaxed = true), mockk(relaxed = true), userPartyRepository, userRepository,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), ObjectMapper(),
            currentUserProvider,
        )
    }

    private val postService by lazy {
        PostService(
            postRepository, userRepository, compPreferenceRepository, preferenceRepository, partyService,
            planRepository, markerRepository, userPartyRepository, mockk(relaxed = true), currentUserProvider,
            chatRoomRepository, mockk(relaxed = true),
        )
    }

    private class Seed(val host: User, val partyUuid: UUID, val postUuid: UUID, val planUuid: UUID)

    private fun seed(): Seed = tx.execute {
        val host = userRepository.save(buildUser())
        val plan = planRepository.save(buildPlan(host))
        val party = partyRepository.save(buildParty())
        userPartyRepository.save(UserParty(party, host, PartyRole.HOST))
        val post = buildPost(party)
        post.author = host
        postRepository.save(post)
        Seed(host, party.uuid, post.uuid, plan.uuid)
    } ?: error("seed failed")

    private fun buildUser(): User {
        val tag = UUID.randomUUID().toString().take(TAG_LENGTH)
        return User(email = "$tag@example.com", nickname = "h$tag", authProvider = AuthProvider.KAKAO, externalId = tag)
    }

    private fun buildPlan(owner: User): Plan {
        val now = Instant.now()
        return Plan(
            title = "Plan", itinStart = now.plus(1, ChronoUnit.DAYS), itinFinish = now.plus(TRIP_DAYS, ChronoUnit.DAYS),
            destination = LOCATION, centerLat = 0.0, centerLng = 0.0, owner = owner,
        )
    }

    private fun buildParty(): Party {
        val now = Instant.now()
        return Party(
            itinStart = now.plus(1, ChronoUnit.DAYS), itinFinish = now.plus(TRIP_DAYS, ChronoUnit.DAYS),
            destination = LOCATION, joined = 1, capacity = CAPACITY, name = "Party",
        )
    }

    private fun buildPost(party: Party) = Post(
        party = party, isInstant = false, title = "Old title", content = "content",
        itinStart = party.itinStart, itinFinish = party.itinFinish, location = LOCATION, capacity = CAPACITY,
    )

    // Same fields as the seeded post, plus a plan: the plan change is what makes the edit
    // write the party row too (party.plan), which is the second lock of the deadlock.
    private fun planChangeRequest(seed: Seed): UpdatePostRequest {
        val post = postRepository.findByUuid(seed.postUuid) ?: error("post missing")
        return UpdatePostRequest(
            title = "New title", content = post.content, isInstant = post.isInstant, itinStart = post.itinStart,
            itinFinish = post.itinFinish, location = post.location, capacity = post.capacity,
            companionSpec = null, planUuid = seed.planUuid, isPlanPublic = true,
        )
    }

    private class Outcome(val completion: Result<Unit>, val edit: Result<Unit>, val editWaitedOnLock: Boolean)

    /** Completion holds the party lock until the edit is seen waiting on a lock, then commits. */
    private fun race(seed: Seed, request: UpdatePostRequest): Outcome {
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val completion = executor.submit<Result<Unit>> {
                runAs(seed.host) {
                    partyService.completeParty(seed.partyUuid)
                    locked.countDown()
                    release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            }
            locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
            val edit = executor.submit<Result<Unit>> { runAs(seed.host) { postService.updatePost(seed.postUuid, request) } }
            val waited = awaitLockWait()
            release.countDown()
            return Outcome(completion.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), edit.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), waited)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun runAs(user: User, block: () -> Unit): Result<Unit> = runCatching {
        currentUserUuid.set(user.uuid)
        tx.executeWithoutResult { block() }
    }

    // performance_schema needs more than the app user's privileges. data_lock_waits, not
    // innodb_trx.trx_state: the latter is a cached view and was seen reporting RUNNING for a
    // whole 10s wait while data_lock_waits already listed it.
    private fun awaitLockWait(): Boolean = asRoot { root ->
        val deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (queryInt(root, COUNT_LOCK_WAITS) == 1) return@asRoot true
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        false
    }

    private fun latestDeadlockReport(): String = asRoot { root ->
        root.createStatement().use { statement ->
            statement.executeQuery(INNODB_STATUS).use { rs ->
                rs.next()
                rs.getString(INNODB_STATUS_COLUMN).substringAfter(DEADLOCK_SECTION, "").substringBefore(TRANSACTIONS_SECTION)
            }
        }
    }

    private fun <T> asRoot(block: (Connection) -> T): T =
        DriverManager.getConnection(mysql.jdbcUrl, ROOT_USER, mysql.password).use(block)

    private fun queryInt(connection: Connection, sql: String): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    private fun describe(result: Result<Unit>): String =
        result.exceptionOrNull()?.let { error -> generateSequence(error) { it.cause }.joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" } }
            ?: "success"

    companion object {
        private val mysql = MySQLContainer(DockerImageName.parse("mysql:8.0.41")).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.datasource.hikari.maximum-pool-size") { POOL_SIZE }
            registry.add("spring.datasource.hikari.minimum-idle") { POOL_SIZE }
        }

        private const val BASE_PACKAGE = "com.dogGetDrunk.meetjyou"
        private const val POOL_SIZE = 5
        private const val ROOT_USER = "root"
        private const val TAG_LENGTH = 8
        private const val CAPACITY = 4
        private const val TRIP_DAYS = 3L
        private const val LOCATION = "Busan"
        private const val TIMEOUT_SECONDS = 30L
        private const val LOCK_WAIT_TIMEOUT_MILLIS = 10_000L
        private const val POLL_INTERVAL_MILLIS = 50L
        private const val COUNT_LOCK_WAITS = "SELECT COUNT(*) FROM performance_schema.data_lock_waits"
        private const val INNODB_STATUS = "SHOW ENGINE INNODB STATUS"
        private const val INNODB_STATUS_COLUMN = "Status"
        private const val DEADLOCK_SECTION = "LATEST DETECTED DEADLOCK"
        private const val TRANSACTIONS_SECTION = "TRANSACTIONS\n"
    }

    init {
        extensions(SpringExtension())

        beforeSpec {
            every { currentUserProvider.uuid } answers { currentUserUuid.get() }
        }

        given("호스트가 파티 완료를 요청해 파티 행 잠금이 잡혀 있을 때 (#165, 교착 허용)") {
            `when`("같은 호스트가 plan을 바꾸는 게시글 수정을 동시에 보내면") {
                then("교착으로 한쪽이 409 대상 예외로 통째로 롤백되고, 이긴 쪽 변경만 남는다") {
                    val seed = seed()
                    val outcome = race(seed, planChangeRequest(seed))
                    log.info("completion={} / edit={}", describe(outcome.completion), describe(outcome.edit))
                    log.info("InnoDB latest deadlock:{}", latestDeadlockReport().ifBlank { " none" })

                    outcome.editWaitedOnLock shouldBe true
                    val completionLost = firstLostDeadlock(outcome.completion, outcome.edit)
                    val post = postRepository.findByUuid(seed.postUuid) ?: error("post missing")
                    val party = partyRepository.findByUuid(seed.partyUuid) ?: error("party missing")
                    if (completionLost) {
                        party.progressStatus shouldNotBe PartyProgressStatus.COMPLETED
                        post.status shouldBe PostStatus.RECRUITING
                        post.title shouldBe "New title"
                    } else {
                        party.progressStatus shouldBe PartyProgressStatus.COMPLETED
                        post.status shouldBe PostStatus.RECRUITMENT_COMPLETED
                        post.title shouldBe "Old title"
                    }
                }
            }
        }
    }
}
