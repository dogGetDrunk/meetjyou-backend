package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipant
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantRepository
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.PlanService
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.post.PostService
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.support.SqlPausePoint
import com.dogGetDrunk.meetjyou.support.firstLostDeadlock
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
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
 * Deadlock candidates of the same shape as #165: two writes inside one party's rows (party, post,
 * user_party, chat) where one path takes the party row lock first and the other touches the rows
 * without it, in a different order. Each scenario stops the "holder" right before one SQL
 * statement (SqlPausePoint) and lets the "contender" run until it waits on a lock, which is the
 * narrow real-world interleaving made deterministic. Accepted deadlocks (rare overlaps) are handled
 * as a 409; those scenarios pin that the loser fails with the mapped exception type and leaves no
 * partial state. The plan scenarios pin that deletePlan no longer deadlocks with or overwrites a
 * concurrent plan switch (#201).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PartyAggregateDeadlockDockerTest : BehaviorSpec() {

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
    @Autowired private lateinit var chatMessageRepository: ChatMessageRepository
    @Autowired private lateinit var chatParticipantRepository: ChatParticipantRepository
    @Autowired private lateinit var userPartyRepository: UserPartyRepository
    @Autowired private lateinit var compPreferenceRepository: CompPreferenceRepository
    @Autowired private lateinit var preferenceRepository: PreferenceRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val log = LoggerFactory.getLogger(javaClass)
    private val currentUserUuid = ThreadLocal<UUID>()
    private val currentUserProvider = mockk<CurrentUserProvider>()
    private val tx by lazy { TransactionTemplate(transactionManager) }

    private val chatParticipantService by lazy {
        ChatParticipantService(
            chatParticipantRepository, chatMessageRepository, chatRoomRepository, userRepository,
            userPartyRepository, mockk(relaxed = true),
        )
    }

    private val partyService by lazy {
        PartyService(
            partyRepository, postRepository, planRepository, markerRepository, chatRoomRepository,
            chatParticipantService, mockk(relaxed = true), userPartyRepository, userRepository,
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

    private val planService by lazy {
        PlanService(
            planRepository, markerRepository, userRepository, postRepository, partyRepository,
            mockk(relaxed = true), currentUserProvider, mockk(relaxed = true),
        )
    }

    private class Seed(val host: User, val member: User, val partyUuid: UUID, val postUuid: UUID, val oldPlan: UUID, val newPlan: UUID)

    private fun seed(): Seed = tx.execute {
        val host = userRepository.save(buildUser())
        val member = userRepository.save(buildUser())
        val oldPlan = planRepository.save(buildPlan(host))
        val newPlan = planRepository.save(buildPlan(host))
        val party = buildParty()
        party.plan = oldPlan
        partyRepository.save(party)
        userPartyRepository.save(UserParty(party, host, PartyRole.HOST))
        userPartyRepository.save(UserParty(party, member, PartyRole.MEMBER))
        val room = chatRoomRepository.save(ChatRoom(party = party))
        chatParticipantRepository.save(ChatParticipant(user = host, room = room))
        chatParticipantRepository.save(ChatParticipant(user = member, room = room))
        val post = postRepository.save(buildPost(party, host, oldPlan))
        Seed(host, member, party.uuid, post.uuid, oldPlan.uuid, newPlan.uuid)
    } ?: error("seed failed")

    private fun buildUser(): User {
        val tag = UUID.randomUUID().toString().take(TAG_LENGTH)
        return User(email = "$tag@example.com", nickname = "u$tag", authProvider = AuthProvider.KAKAO, externalId = tag)
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
            destination = LOCATION, joined = 2, capacity = CAPACITY, name = "Party",
        )
    }

    private fun buildPost(party: Party, author: User, plan: Plan): Post {
        val post = Post(
            party = party, isInstant = false, title = "Title", content = "content",
            itinStart = party.itinStart, itinFinish = party.itinFinish, location = LOCATION, capacity = CAPACITY,
        )
        post.author = author
        post.plan = plan
        post.isPlanPublic = true
        return post
    }

    private fun switchPlanRequest(seed: Seed): UpdatePostRequest {
        val post = postRepository.findByUuid(seed.postUuid) ?: error("post missing")
        return UpdatePostRequest(
            title = post.title, content = post.content, isInstant = post.isInstant, itinStart = post.itinStart,
            itinFinish = post.itinFinish, location = post.location, capacity = post.capacity,
            companionSpec = null, planUuid = seed.newPlan, isPlanPublic = true,
        )
    }

    private class Outcome(val holder: Result<Unit>, val contender: Result<Unit>, val contenderWaitedOnLock: Boolean)

    /** Holder runs until right before [pauseBeforeSql]; contender then runs until it waits on a lock. */
    private fun race(pauseBeforeSql: String, holder: Pair<User, () -> Unit>, contender: Pair<User, () -> Unit>): Outcome {
        val point = SqlPausePoint.Point(pauseBeforeSql)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val holderDone = executor.submit<Result<Unit>> {
                SqlPausePoint.arm(point)
                try { runAs(holder.first, holder.second) } finally { SqlPausePoint.disarm() }
            }
            point.reached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
            val contenderDone = executor.submit<Result<Unit>> { runAs(contender.first, contender.second) }
            val waited = awaitLockWait()
            point.release.countDown()
            return Outcome(holderDone.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), contenderDone.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), waited)
        } finally {
            point.release.countDown()
            executor.shutdownNow()
        }
    }

    /** Holder runs until right before [pauseBeforeSql]; intruder then runs to commit before the holder resumes. */
    private fun interleave(pauseBeforeSql: String, holder: Pair<User, () -> Unit>, intruder: Pair<User, () -> Unit>): Pair<Result<Unit>, Result<Unit>> {
        val point = SqlPausePoint.Point(pauseBeforeSql)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val holderDone = executor.submit<Result<Unit>> {
                SqlPausePoint.arm(point)
                try { runAs(holder.first, holder.second) } finally { SqlPausePoint.disarm() }
            }
            point.reached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
            val intruderResult = runAs(intruder.first, intruder.second)
            point.release.countDown()
            return holderDone.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) to intruderResult
        } finally {
            point.release.countDown()
            executor.shutdownNow()
        }
    }

    private fun runAs(user: User, block: () -> Unit): Result<Unit> = runCatching {
        currentUserUuid.set(user.uuid)
        tx.executeWithoutResult { block() }
    }

    // data_lock_waits, not innodb_trx.trx_state: the latter is a cached view that can miss a wait.
    private fun awaitLockWait(): Boolean = asRoot { root ->
        val deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (queryInt(root, COUNT_LOCK_WAITS) >= 1) return@asRoot true
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

    private fun report(outcome: Outcome) {
        log.info("holder={} / contender={}", describe(outcome.holder), describe(outcome.contender))
        log.info("InnoDB latest deadlock:{}", latestDeadlockReport().ifBlank { " none" })
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
            registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector") { SqlPausePoint::class.java.name }
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
        private const val DELETE_CHAT_PARTICIPANT = "delete from chat_participant"
        private const val UPDATE_PARTY = "update party"
        private const val FIRST_UPDATE = "update "
    }

    init {
        extensions(SpringExtension())

        beforeSpec {
            every { currentUserProvider.uuid } answers { currentUserUuid.get() }
        }

        given("멤버가 파티를 나가는 중일 때") {
            `when`("호스트가 같은 파티를 동시에 삭제하면") {
                then("교착으로 한쪽이 409 대상 예외로 통째로 롤백되고, 이긴 쪽 변경만 남는다") {
                    val seed = seed()
                    // Leave flushes user_party early (auto-flush on the chat room lookup) and
                    // deletes its chat_participant row last, at commit.
                    val outcome = race(
                        pauseBeforeSql = DELETE_CHAT_PARTICIPANT,
                        holder = seed.member to { partyService.leaveParty(seed.partyUuid) },
                        contender = seed.host to { partyService.deleteParty(seed.partyUuid) },
                    )
                    report(outcome)

                    outcome.contenderWaitedOnLock shouldBe true
                    val leaveLost = firstLostDeadlock(outcome.holder, outcome.contender)
                    if (leaveLost) {
                        partyRepository.findByUuid(seed.partyUuid).shouldBeNull()
                        chatRoomRepository.findByParty_Uuid(seed.partyUuid).shouldBeNull()
                    } else {
                        val party = partyRepository.findByUuid(seed.partyUuid) ?: error("party missing")
                        party.joined shouldBe 1
                        tx.execute { userPartyRepository.findByParty_UuidAndUser_Uuid(seed.partyUuid, seed.member.uuid)?.memberStatus } shouldBe MemberStatus.LEFT
                    }
                }
            }
        }

        given("호스트가 게시글의 여행 계획서를 다른 것으로 바꾸는 중일 때") {
            `when`("같은 호스트가 기존 계획서를 동시에 삭제하면") {
                then("삭제는 게시글 행에서 기다렸다가 교착 없이 옛 계획서만 떼어내고, 새 계획서 연결은 남는다") {
                    val seed = seed()
                    val request = switchPlanRequest(seed)
                    // The edit's flush writes post, then party; it stops between the two. deletePlan
                    // detaches post first, so it waits on the post row before touching party — the
                    // same order as the edit, which removed the deadlock accepted in #165.
                    val outcome = race(
                        pauseBeforeSql = UPDATE_PARTY,
                        holder = seed.host to { postService.updatePost(seed.postUuid, request) },
                        contender = seed.host to { planService.deletePlan(seed.oldPlan) },
                    )
                    report(outcome)

                    outcome.contenderWaitedOnLock shouldBe true
                    outcome.holder.exceptionOrNull().shouldBeNull()
                    outcome.contender.exceptionOrNull().shouldBeNull()
                    val linked = tx.execute {
                        val post = postRepository.findByUuid(seed.postUuid) ?: error("post missing")
                        post.plan?.uuid to post.party.plan?.uuid
                    }
                    linked shouldBe (seed.newPlan to seed.newPlan)
                    planRepository.findByUuid(seed.oldPlan).shouldBeNull()
                }
            }
        }

        given("호스트가 기존 계획서를 삭제하는 중일 때 (#201)") {
            `when`("삭제가 쓰기 직전인 사이에 같은 호스트가 게시글 계획서를 새것으로 바꿔 커밋하면") {
                then("삭제는 옛 계획서만 떼어내고, 방금 연결한 새 계획서 연결은 남는다") {
                    val seed = seed()
                    val request = switchPlanRequest(seed)
                    // deletePlan has read the old links but written nothing yet, so it holds no row
                    // lock and the edit commits without waiting.
                    val (deleteResult, editResult) = interleave(
                        pauseBeforeSql = FIRST_UPDATE,
                        holder = seed.host to { planService.deletePlan(seed.oldPlan) },
                        intruder = seed.host to { postService.updatePost(seed.postUuid, request) },
                    )
                    log.info("delete={} / edit={}", describe(deleteResult), describe(editResult))

                    editResult.exceptionOrNull().shouldBeNull()
                    deleteResult.exceptionOrNull().shouldBeNull()
                    val linked = tx.execute {
                        val post = postRepository.findByUuid(seed.postUuid) ?: error("post missing")
                        post.plan?.uuid to post.party.plan?.uuid
                    }
                    linked shouldBe (seed.newPlan to seed.newPlan)
                    planRepository.findByUuid(seed.oldPlan).shouldBeNull()
                }
            }
        }
    }
}
