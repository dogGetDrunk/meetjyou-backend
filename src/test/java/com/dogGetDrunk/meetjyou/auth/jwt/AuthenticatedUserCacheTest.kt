package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.UserStatus
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.github.benmanes.caffeine.cache.Ticker
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AuthenticatedUserCacheTest : BehaviorSpec() {

    private val userRepository = mockk<UserRepository>()
    private lateinit var ticker: FakeTicker
    private lateinit var cache: AuthenticatedUserCache
    private lateinit var user: User

    init {
        // Rebuilt per test: Kotest can run more than one leaf on the same spec instance, and a
        // cache entry left by an earlier test would mask the case under test.
        beforeEach {
            clearMocks(userRepository)
            ticker = FakeTicker()
            cache = AuthenticatedUserCache(userRepository, ticker)
            user = UserFixtures.user()
            every { userRepository.findByUuid(user.uuid) } returns user
        }

        given("활성 유저를") {
            `when`("TTL 안에 다시 조회하면") {
                then("DB를 한 번만 조회한다") {
                    cache.findActive(user.uuid) shouldBe AuthenticatedUser(user.uuid, Role.USER)
                    ticker.advance(AuthenticatedUserCache.TTL.minusMillis(1))
                    cache.findActive(user.uuid) shouldBe AuthenticatedUser(user.uuid, Role.USER)

                    verify(exactly = 1) { userRepository.findByUuid(user.uuid) }
                }
            }
            `when`("TTL 내내 계속 조회해도") {
                then("처음 적재 시점부터 TTL이 지나면 다시 조회한다 (접근이 만료를 늦추지 않는다)") {
                    cache.findActive(user.uuid)
                    ticker.advance(AuthenticatedUserCache.TTL.minusSeconds(1))
                    cache.findActive(user.uuid)
                    ticker.advance(Duration.ofSeconds(1))
                    cache.findActive(user.uuid)

                    verify(exactly = 2) { userRepository.findByUuid(user.uuid) }
                }
            }
            `when`("여러 요청이 동시에 처음 조회하면") {
                then("DB 조회는 한 번으로 합쳐진다") {
                    val loads = AtomicInteger()
                    val release = CountDownLatch(1)
                    every { userRepository.findByUuid(user.uuid) } answers {
                        loads.incrementAndGet()
                        release.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        user
                    }
                    val pool = Executors.newFixedThreadPool(CONCURRENT_CALLS)
                    val results = (1..CONCURRENT_CALLS)
                        .map { pool.submit<AuthenticatedUser?> { cache.findActive(user.uuid) } }
                    Thread.sleep(SETTLE_MILLIS)
                    release.countDown()

                    results.map { it.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS) }.toSet() shouldBe
                        setOf(AuthenticatedUser(user.uuid, Role.USER))
                    loads.get() shouldBe 1
                    pool.shutdown()
                }
            }
        }

        given("비활성이거나 없는 유저는") {
            listOf(UserStatus.DELETED, UserStatus.PURGED).forEach { inactive ->
                `when`("status가 ${inactive}이면") {
                    then("null을 돌려주고 캐시하지 않는다") {
                        user.status = inactive
                        cache.findActive(user.uuid) shouldBe null
                        cache.findActive(user.uuid) shouldBe null

                        verify(exactly = 2) { userRepository.findByUuid(user.uuid) }
                    }
                }
            }
            `when`("DB에 없으면") {
                then("null을 돌려주고 캐시하지 않는다") {
                    every { userRepository.findByUuid(user.uuid) } returns null
                    cache.findActive(user.uuid) shouldBe null
                    cache.findActive(user.uuid) shouldBe null

                    verify(exactly = 2) { userRepository.findByUuid(user.uuid) }
                }
            }
        }

        given("캐시된 유저를 evictAfterCommit 하면") {
            `when`("트랜잭션 밖이면") {
                then("바로 지워서 다음 조회가 DB의 새 상태를 본다") {
                    cache.findActive(user.uuid)
                    user.status = UserStatus.DELETED
                    cache.evictAfterCommit(user.uuid)

                    cache.findActive(user.uuid) shouldBe null
                }
            }
            `when`("트랜잭션 안이면") {
                then("트랜잭션이 끝날 때 지운다") {
                    cache.findActive(user.uuid)
                    TransactionSynchronizationManager.initSynchronization()
                    try {
                        cache.evictAfterCommit(user.uuid)
                        user.status = UserStatus.DELETED
                        // Still the pre-commit entry: other requests can't see the change yet either.
                        cache.findActive(user.uuid) shouldBe AuthenticatedUser(user.uuid, Role.USER)

                        TransactionSynchronizationManager.getSynchronizations()
                            .forEach { it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED) }
                    } finally {
                        TransactionSynchronizationManager.clearSynchronization()
                    }

                    cache.findActive(user.uuid) shouldBe null
                }
            }
        }
    }
}

private const val CONCURRENT_CALLS = 8
private const val LATCH_TIMEOUT_SECONDS = 5L
private const val SETTLE_MILLIS = 200L

private class FakeTicker : Ticker {
    private val nanos = AtomicLong()
    override fun read(): Long = nanos.get()
    fun advance(duration: Duration) {
        nanos.addAndGet(duration.toNanos())
    }
}
