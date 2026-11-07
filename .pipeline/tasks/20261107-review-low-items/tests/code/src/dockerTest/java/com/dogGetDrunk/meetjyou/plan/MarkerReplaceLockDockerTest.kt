package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.plan.dto.CreateMarkerRequest
import com.dogGetDrunk.meetjyou.plan.dto.MarkerResponse
import com.dogGetDrunk.meetjyou.support.SharedMySql
import com.dogGetDrunk.meetjyou.support.assertWaitedOnLock
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import jakarta.persistence.EntityManager
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

// AT-005-1: replaceMarkers must wait on the plan row lock and then replace against the latest commit.
// The service is built by hand, so the test wraps each call in a transaction like the real proxy does.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MarkerReplaceLockDockerTest : BehaviorSpec() {
    @SpringBootConfiguration
    @EntityScan(basePackages = [BASE_PACKAGE])
    @EnableJpaRepositories(basePackages = [BASE_PACKAGE])
    class Config

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var plans: PlanRepository
    @Autowired private lateinit var markers: MarkerRepository
    @Autowired private lateinit var manager: PlatformTransactionManager
    @Autowired private lateinit var entityManager: EntityManager
    private val provider = mockk<CurrentUserProvider>()
    private val tx by lazy { TransactionTemplate(manager) }
    private val service by lazy { MarkerService(markers, plans, mockk(relaxed = true), provider) }

    private data class Seed(val owner: UUID, val plan: UUID)
    private data class Shape(val place: String, val dayNum: Int, val idx: Int)

    private fun requests(prefix: String, count: Int) = (0 until count).map {
        CreateMarkerRequest(
            lat = BASE_LAT + it, lng = BASE_LNG + it, date = DATE, dayNum = it + 1, idx = it,
            place = "$prefix$it", memo = "memo-$prefix$it",
        )
    }

    private fun toMarkers(requests: List<CreateMarkerRequest>, plan: Plan) = requests.map {
        Marker(it.lat, it.lng, it.date, it.dayNum, it.idx, it.place, it.memo, plan)
    }

    private fun seed(): Seed = tx.execute {
        val tag = UUID.randomUUID().toString().take(TAG_LENGTH)
        val owner = users.save(
            User(email = "$tag@example.com", nickname = "o$tag", authProvider = AuthProvider.KAKAO, externalId = tag),
        )
        val plan = plans.save(Plan("Trip", DATE, DATE, "Busan", BASE_LAT, BASE_LNG, owner = owner))
        markers.saveAll(toMarkers(requests(INITIAL_PREFIX, INITIAL_COUNT), plan))
        Seed(owner.uuid, plan.uuid)
    } ?: error("seed failed")

    // Holder: lock the plan row first, then swap the markers to set X, and stay uncommitted.
    private fun holdAndReplaceWithX(seed: Seed, ready: CountDownLatch, release: CountDownLatch) =
        tx.executeWithoutResult {
            entityManager.createNativeQuery("SELECT id FROM plan WHERE uuid = ?1 FOR UPDATE")
                .setParameter(1, seed.plan.toString())
                .resultList
            val plan = plans.findByUuid(seed.plan) ?: error("plan missing")
            markers.deleteAllByPlan(plan)
            markers.saveAll(toMarkers(requests(HELD_PREFIX, HELD_COUNT), plan))
            markers.flush()
            ready.countDown()
            check(release.await(TIMEOUT, TimeUnit.SECONDS)) { "holder was never released" }
        }

    private fun waitedOnLock(): Boolean {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (mysql.countLockWaits() > 0) return true
            Thread.sleep(POLL_MILLIS)
        }
        return false
    }

    private fun replaceWhileHeld(seed: Seed, replacement: List<CreateMarkerRequest>): List<MarkerResponse> {
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val holder = executor.submit { holdAndReplaceWithX(seed, ready, release) }
            ready.await(TIMEOUT, TimeUnit.SECONDS) shouldBe true
            val contender = executor.submit<List<MarkerResponse>> {
                tx.execute { service.replaceMarkers(seed.plan, replacement) } ?: error("no result")
            }
            val observed = waitedOnLock()
            release.countDown()
            holder.get(TIMEOUT, TimeUnit.SECONDS)
            val outcome = runCatching { contender.get(TIMEOUT, TimeUnit.SECONDS) }
            assertWaitedOnLock(observed, outcome)
            return outcome.getOrThrow()
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun committedShapes(plan: UUID): List<Shape> = tx.execute {
        markers.findAllByPlan_UuidOrderByDayNumAscIdxAsc(plan).map { Shape(it.place, it.dayNum, it.idx) }
    } ?: error("read failed")

    init {
        extensions(SpringExtension())
        given("AT-005-1 plan 행 잠금과 마커 집합 X를 커밋하지 않은 보유 트랜잭션이 있을 때") {
            `when`("replaceMarkers가 집합 Y로 호출되고 보유 트랜잭션이 커밋되면") {
                then("잠금 대기 후 성공하고 최종 마커는 Y와 정확히 같다") {
                    val seed = seed()
                    every { provider.uuid } returns seed.owner
                    val replacement = requests(REPLACEMENT_PREFIX, REPLACEMENT_COUNT)
                    val expected = replacement.map { Shape(it.place, it.dayNum, it.idx) }

                    val returned = replaceWhileHeld(seed, replacement)

                    returned.map { Shape(it.place, it.dayNum, it.idx) } shouldBe expected
                    val committed = committedShapes(seed.plan)
                    committed shouldBe expected
                    committed.count { it.place.startsWith(HELD_PREFIX) } shouldBe 0
                    committed.count { it.place.startsWith(INITIAL_PREFIX) } shouldBe 0
                }
            }
        }
    }

    companion object {
        private val mysql = SharedMySql.newDatabase()

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
        }

        private const val BASE_PACKAGE = "com.dogGetDrunk.meetjyou"
        private const val TAG_LENGTH = 8
        private const val INITIAL_PREFIX = "W-"
        private const val HELD_PREFIX = "X-"
        private const val REPLACEMENT_PREFIX = "Y-"
        private const val INITIAL_COUNT = 2
        private const val HELD_COUNT = 3
        private const val REPLACEMENT_COUNT = 2
        private const val BASE_LAT = 35.0
        private const val BASE_LNG = 129.0
        private const val TIMEOUT = 30L
        private const val WAIT_MILLIS = 10_000L
        private const val POLL_MILLIS = 50L
        private val DATE = Instant.parse("2030-05-01T00:00:00Z")
    }
}
