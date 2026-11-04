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
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.support.SharedMySql
import com.dogGetDrunk.meetjyou.support.assertWaitedOnLock
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

/** Completion and editing serialize on the party row; a completed party rejects the edit. */
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

    private val currentUserUuid = ThreadLocal<UUID>()
    private val currentUserProvider = mockk<CurrentUserProvider>()
    private val tx by lazy { TransactionTemplate(transactionManager) }

    private val partyService by lazy {
        PartyService(
            partyRepository, postRepository, planRepository, markerRepository, chatRoomRepository,
            mockk(relaxed = true), mockk(relaxed = true), userPartyRepository, userRepository,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), ObjectMapper().findAndRegisterModules(),
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

    private class Seed(val host: User, val partyUuid: UUID, val postUuid: UUID, val oldPlan: UUID, val newPlan: UUID)

    private fun seed(): Seed = tx.execute {
        val host = userRepository.save(buildUser())
        val plan = planRepository.save(buildPlan(host))
        val newPlan = planRepository.save(buildPlan(host))
        val party = buildParty()
        party.plan = plan
        partyRepository.save(party)
        userPartyRepository.save(UserParty(party, host, PartyRole.HOST))
        val post = buildPost(party)
        post.author = host
        post.plan = plan
        post.isPlanPublic = true
        postRepository.save(post)
        Seed(host, party.uuid, post.uuid, plan.uuid, newPlan.uuid)
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

    private fun editRequest(seed: Seed, changePlan: Boolean): UpdatePostRequest {
        val post = postRepository.findByUuid(seed.postUuid) ?: error("post missing")
        return UpdatePostRequest(
            title = "New title", content = post.content, isInstant = post.isInstant,
            itinStart = post.itinStart.plus(EDIT_OFFSET_DAYS, ChronoUnit.DAYS),
            itinFinish = post.itinFinish.plus(EDIT_OFFSET_DAYS, ChronoUnit.DAYS),
            location = "Jeju", capacity = NEW_CAPACITY,
            companionSpec = null, planUuid = if (changePlan) seed.newPlan else seed.oldPlan, isPlanPublic = true,
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
                    check(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "completion release timed out" }
                }
            }
            locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
            val edit = executor.submit<Result<Unit>> { runAs(seed.host) { postService.updatePost(seed.postUuid, request) } }
            val waited = awaitLockWait()
            release.countDown()
            return Outcome(completion.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), edit.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), waited)
        } finally {
            release.countDown()
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
    private fun awaitLockWait(): Boolean {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (mysql.countLockWaits() == 1) return true
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    companion object {
        private val mysql = SharedMySql.newDatabase()

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
        private const val TAG_LENGTH = 8
        private const val CAPACITY = 4
        private const val NEW_CAPACITY = 6
        private const val TRIP_DAYS = 3L
        private const val EDIT_OFFSET_DAYS = 30L
        private const val LOCATION = "Busan"
        private const val TIMEOUT_SECONDS = 30L
        private const val LOCK_WAIT_TIMEOUT_MILLIS = 10_000L
        private const val POLL_INTERVAL_MILLIS = 50L
    }

    init {
        extensions(SpringExtension())

        beforeSpec {
            every { currentUserProvider.uuid } answers { currentUserUuid.get() }
        }

        given("완료 트랜잭션이 파티 락을 보유한 경우") {
            listOf(false, true).forEach { changePlan ->
                `when`("AT-004-1 계획서 변경 여부가 $changePlan 인 편집이 대기하면") {
                    then("완료 후 편집은 거부되고 기존 데이터가 유지된다") {
                        val seed = seed()
                        val original = tx.execute {
                            val post = postRepository.findByUuid(seed.postUuid) ?: error("post missing")
                            post.itinStart to post.itinFinish
                        } ?: error("snapshot missing")
                        val outcome = race(seed, editRequest(seed, changePlan))
                        assertWaitedOnLock(outcome.editWaitedOnLock, outcome.edit)
                        outcome.completion.exceptionOrNull().shouldBeNull()
                        outcome.edit.exceptionOrNull().shouldBeInstanceOf<InvalidInputException>()
                        tx.executeWithoutResult {
                            val post = postRepository.findByUuid(seed.postUuid) ?: error("post missing")
                            val party = partyRepository.findByUuid(seed.partyUuid) ?: error("party missing")
                            party.progressStatus shouldBe PartyProgressStatus.COMPLETED
                            party.recruitmentStatus shouldBe PartyRecruitmentStatus.CLOSED
                            post.status shouldBe PostStatus.RECRUITMENT_COMPLETED
                            post.title shouldBe "Old title"
                            post.capacity shouldBe CAPACITY
                            party.capacity shouldBe CAPACITY
                            post.location shouldBe LOCATION
                            party.destination shouldBe LOCATION
                            post.itinStart shouldBe original.first
                            post.itinFinish shouldBe original.second
                            party.itinStart shouldBe original.first
                            party.itinFinish shouldBe original.second
                            post.plan?.uuid shouldBe seed.oldPlan
                            party.plan?.uuid shouldBe seed.oldPlan
                            post.isPlanPublic shouldBe true
                        }
                    }
                }
            }
        }
    }
}
