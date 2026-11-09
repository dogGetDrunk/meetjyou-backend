package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.support.PlaceTestDoubles
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyCapacityBelowJoinedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.post.PostService
import com.dogGetDrunk.meetjyou.post.PostStatus
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostStatusRequest
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.support.SharedMySql
import com.dogGetDrunk.meetjyou.support.assertWaitedOnLock
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
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
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

// The preload variant covers both the existing persistence-context entry and the RR snapshot.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostLockFreshReadDockerTest : BehaviorSpec() {
    @SpringBootConfiguration
    @EntityScan(basePackages = [BASE_PACKAGE])
    @EnableJpaRepositories(basePackages = [BASE_PACKAGE])
    class Config

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var parties: PartyRepository
    @Autowired private lateinit var posts: PostRepository
    @Autowired private lateinit var plans: PlanRepository
    @Autowired private lateinit var markers: MarkerRepository
    @Autowired private lateinit var rooms: ChatRoomRepository
    @Autowired private lateinit var memberships: UserPartyRepository
    @Autowired private lateinit var compPreferences: CompPreferenceRepository
    @Autowired private lateinit var preferences: PreferenceRepository
    @Autowired private lateinit var manager: PlatformTransactionManager
    private val current = ThreadLocal<UUID>()
    private val provider = mockk<CurrentUserProvider>()
    private val tx by lazy { TransactionTemplate(manager) }
    private val partyService by lazy {
        PartyService(parties, posts, plans, markers, rooms, mockk(relaxed = true), mockk(relaxed = true),
            memberships, users, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), ObjectMapper().findAndRegisterModules(), provider,
                PlaceTestDoubles.regionCatalog())
    }
    private val service by lazy {
        PostService(posts, users, compPreferences, preferences, partyService, plans, markers, memberships,
            mockk(relaxed = true), provider, rooms, mockk(relaxed = true),
                PlaceTestDoubles.resolver(), PlaceTestDoubles.regionCatalog())
    }
    private data class Seed(val host: UUID, val applicant: UUID, val party: UUID, val post: UUID)
    private fun user(): User {
        val tag = UUID.randomUUID().toString().take(TAG_LENGTH)
        return users.save(User(email = "$tag@example.com", nickname = "h$tag", authProvider = AuthProvider.KAKAO, externalId = tag))
    }
    private fun seed(joined: Int = INITIAL_JOINED): Seed = tx.execute {
        val host = user()
        val applicant = user()
        val party = parties.save(Party(itinStart = START, itinFinish = FINISH, destination = LOCATION,
            joined = joined, capacity = INITIAL_CAPACITY, name = "Party"))
        memberships.save(UserParty(party, host, PartyRole.HOST))
        memberships.save(UserParty(party, applicant, PartyRole.MEMBER).also { it.pending() })
        val post = Post(party, false, "Old title", "content", START, FINISH, LOCATION, INITIAL_CAPACITY)
        post.author = host
        posts.save(post)
        Seed(host.uuid, applicant.uuid, party.uuid, post.uuid)
    } ?: error("seed failed")
    private fun request(capacity: Int) = UpdatePostRequest("New title", "content", false, START, FINISH,
        LOCATION, capacity, null, null, null)
    private fun runAs(uuid: UUID, action: () -> Unit): Result<Unit> = runCatching {
        current.set(uuid)
        try { tx.executeWithoutResult { action() } } finally { current.remove() }
    }
    private fun waited(): Boolean {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (mysql.countLockWaits() > 0) return true
            Thread.sleep(POLL_MILLIS)
        }
        return false
    }
    private fun race(seed: Seed, first: () -> Unit, second: () -> Unit): Pair<Result<Unit>, Result<Unit>> {
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val a = executor.submit<Result<Unit>> { runAs(seed.host) { first(); ready.countDown(); check(release.await(TIMEOUT, TimeUnit.SECONDS)) } }
            ready.await(TIMEOUT, TimeUnit.SECONDS) shouldBe true
            val b = executor.submit<Result<Unit>> { runAs(seed.host) { posts.findByUuid(seed.post) ?: error("post missing"); second() } }
            val observed = waited()
            release.countDown()
            val firstResult = a.get(TIMEOUT, TimeUnit.SECONDS)
            val secondResult = b.get(TIMEOUT, TimeUnit.SECONDS)
            assertWaitedOnLock(observed, secondResult)
            return firstResult to secondResult
        } finally { release.countDown(); executor.shutdownNow() }
    }
    private fun verifyCapacity(seed: Seed, expected: Int, joined: Int) = tx.executeWithoutResult {
        val post = posts.findByUuid(seed.post) ?: error("post missing")
        val party = parties.findByUuid(seed.party) ?: error("party missing")
        post.capacity shouldBe expected
        party.capacity shouldBe expected
        party.joined shouldBe joined
    }
    init {
        extensions(SpringExtension())
        beforeSpec { every { provider.uuid } answers { current.get() } }
        given("AT-006-1 이전 게시글을 읽은 트랜잭션이 있을 때") {
            `when`("정원 증원 커밋을 기다린 후 원래 정원으로 수정하면") {
                then("게시글과 파티가 모두 원래 정원으로 커밋된다") {
                    val seed = seed()
                    val (a, b) = race(seed, { service.updatePost(seed.post, request(INCREASED_CAPACITY)) },
                        { service.updatePost(seed.post, request(INITIAL_CAPACITY)) })
                    a.isSuccess shouldBe true
                    b.isSuccess shouldBe true
                    verifyCapacity(seed, INITIAL_CAPACITY, INITIAL_JOINED)
                }
            }
        }
        given("AT-006-2 이전 모집 상태를 읽은 트랜잭션이 있을 때") {
            `when`("모집 완료 커밋을 기다린 후 모집을 재개하면") {
                then("게시글과 파티가 모두 모집 중으로 커밋된다") {
                    val seed = seed()
                    val (a, b) = race(seed, { service.updatePostStatus(seed.post, UpdatePostStatusRequest(PostStatus.RECRUITMENT_COMPLETED)) },
                        { service.updatePostStatus(seed.post, UpdatePostStatusRequest(PostStatus.RECRUITING)) })
                    a.isSuccess shouldBe true
                    b.isSuccess shouldBe true
                    tx.executeWithoutResult {
                        posts.findByUuid(seed.post)?.status shouldBe PostStatus.RECRUITING
                        parties.findByUuid(seed.party)?.recruitmentStatus shouldBe PartyRecruitmentStatus.OPEN
                    }
                }
            }
        }
        given("AT-002-2 승인 전 스냅샷을 읽은 정원 수정이 있을 때") {
            `when`("승인이 파티 잠금을 해제한 후 정원을 줄이면") {
                then("최신 참여 인원으로 축소를 거부하고 승인을 유지한다") {
                    val seed = seed(JOINED_BEFORE_APPROVAL)
                    val (a, b) = race(seed, { partyService.approveJoinRequest(seed.party, seed.applicant) },
                        { service.updatePost(seed.post, request(JOINED_BEFORE_APPROVAL)) })
                    a.isSuccess shouldBe true
                    (b.exceptionOrNull() is PartyCapacityBelowJoinedException) shouldBe true
                    verifyCapacity(seed, INITIAL_CAPACITY, INITIAL_CAPACITY)
                    tx.executeWithoutResult {
                        memberships.findByParty_UuidAndUser_Uuid(seed.party, seed.applicant)?.memberStatus shouldBe MemberStatus.JOINED
                    }
                }
            }
        }
    }
    companion object {
        private val mysql = SharedMySql.newDatabase()
        @JvmStatic @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
        }
        private const val BASE_PACKAGE = "com.dogGetDrunk.meetjyou"
        private const val TAG_LENGTH = 8
        private const val INITIAL_JOINED = 1
        private const val JOINED_BEFORE_APPROVAL = 2
        private const val INITIAL_CAPACITY = 3
        private const val INCREASED_CAPACITY = 10
        private const val TIMEOUT = 30L
        private const val WAIT_MILLIS = 10_000L
        private const val POLL_MILLIS = 50L
        private const val LOCATION = "Busan"
        private val START = Instant.parse("2030-05-01T00:00:00Z")
        private val FINISH = Instant.parse("2030-05-03T00:00:00Z")
    }
}
