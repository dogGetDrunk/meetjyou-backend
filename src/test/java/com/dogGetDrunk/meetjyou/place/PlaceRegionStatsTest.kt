package com.dogGetDrunk.meetjyou.place

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.transaction.PlatformTransactionManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// AT-005 (unit): per-region counts are cached, dropped on invalidate, and never re-published stale.
class PlaceRegionStatsTest : BehaviorSpec({
    given("건수를 한 번 읽은 뒤") {
        then("다시 묻는 동안 DB를 다시 읽지 않는다") {
            val fixture = Fixture({ listOf("11680" to 3L, "11650" to 2L) })
            fixture.sut.activeCount(setOf("11680", "11650")) shouldBe 5L
            fixture.sut.activeCount(setOf("11680")) shouldBe 3L
            verify(exactly = 1) { fixture.jdbc.query(any<String>(), any<RowMapper<Pair<String, Long>>>()) }
        }
        then("invalidate 후 다음 호출이 새 건수를 읽는다") {
            val fixture = Fixture({ listOf("11680" to 3L) }, { listOf("11680" to 7L) })
            fixture.sut.activeCount(setOf("11680")) shouldBe 3L
            fixture.sut.invalidate()
            fixture.sut.activeCount(setOf("11680")) shouldBe 7L
        }
    }

    given("읽는 도중 invalidate되면") {
        then("그 읽기 결과는 캐시에 남지 않는다") {
            val loading = CountDownLatch(1)
            val release = CountDownLatch(1)
            val fixture = Fixture(
                {
                    loading.countDown()
                    release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                    listOf("11680" to 1L)
                },
                { listOf("11680" to 9L) },
            )

            val stale = CompletableFuture.supplyAsync { fixture.sut.activeCount(setOf("11680")) }
            loading.await(WAIT_SECONDS, TimeUnit.SECONDS)
            fixture.sut.invalidate()
            release.countDown()

            stale.get(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe 1L
            fixture.sut.activeCount(setOf("11680")) shouldBe 9L
        }
    }

    given("읽기가 실패하면") {
        then("예외를 전하고 다음 호출이 다시 읽는다") {
            val fixture = Fixture({ throw DataAccessResourceFailureException("down") }, { listOf("11680" to 4L) })
            shouldThrow<DataAccessResourceFailureException> { fixture.sut.activeCount(setOf("11680")) }
            fixture.sut.activeCount(setOf("11680")) shouldBe 4L
        }
    }
}) {
    // One per leaf: the cache lives in the instance, and a spec instance may run several leaves.
    private class Fixture(vararg reads: () -> List<Pair<String, Long>>) {
        private val queue = ArrayDeque(reads.toList())
        val jdbc = mockk<JdbcTemplate> {
            every { query(any<String>(), any<RowMapper<Pair<String, Long>>>()) } answers { queue.removeFirst()() }
        }
        val sut = PlaceRegionStats(jdbc, mockk<PlatformTransactionManager>(relaxed = true))
    }

    private companion object {
        const val WAIT_SECONDS = 5L
    }
}
