package com.dogGetDrunk.meetjyou.version

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val OLD_VERSION = "1.0.0"
private const val NEW_VERSION = "2.0.0"
private const val WAIT_SECONDS = 5L
private const val POLL_MILLIS = 10L

/**
 * AT-006-1: a /check lookup that started before a write must not leave its stale summary in the cache.
 * Every latch and thread wait is bounded so a deadlock ends as a failure, not a hang.
 */
class AppVersionCacheStaleTest : BehaviorSpec({

    given("AT-006-1 a lookup that is paused inside the DB read while a write invalidates the cache") {
        `when`("the write runs, the lookup resumes, and /check is called again") {
            then("AT-006-1 the next checkVersion reflects the new version list") {
                val fixture = StaleFixture()

                val reader = fixture.startThread { fixture.sut.checkVersion(Platform.IOS, OLD_VERSION) }
                fixture.entered.await(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe true

                val writer = fixture.startThread { fixture.sut.setForceUpdate(NEW_VERSION, Platform.IOS, true) }
                fixture.awaitFinishedOrBlocked(writer) shouldBe true

                fixture.release.countDown()
                fixture.join(reader)
                fixture.join(writer)
                fixture.failure.get() shouldBe null

                val result = fixture.sut.checkVersion(Platform.IOS, OLD_VERSION)

                result.latestVersion shouldBe NEW_VERSION
                result.updateRequired shouldBe true
            }
        }
    }

    given("AT-006-1 sequential write then lookup (behavior kept)") {
        `when`("a write completes before /check is called") {
            then("AT-006-1 checkVersion reflects the new version list") {
                val fixture = StaleFixture(pauseFirstRead = false)

                fixture.sut.checkVersion(Platform.IOS, OLD_VERSION)
                fixture.sut.setForceUpdate(NEW_VERSION, Platform.IOS, true)
                val result = fixture.sut.checkVersion(Platform.IOS, OLD_VERSION)

                result.latestVersion shouldBe NEW_VERSION
                result.updateRequired shouldBe true
            }
        }
    }
})

private class StaleFixture(pauseFirstRead: Boolean = true) {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>(null)

    private val readCount = AtomicInteger(0)
    private val appVersionRepository: AppVersionRepository = mockk(relaxed = true)
    private val platformStoreUrlRepository: PlatformStoreUrlRepository = mockk(relaxed = true)
    val sut = AppVersionService(appVersionRepository, platformStoreUrlRepository)

    init {
        every { platformStoreUrlRepository.findById(any()) } returns Optional.empty()
        every { appVersionRepository.save(any<AppVersion>()) } answers { firstArg() }
        every { appVersionRepository.findByVersionAndPlatform(NEW_VERSION, Platform.IOS) } returns
            version(NEW_VERSION, forceUpdate = false)
        every { appVersionRepository.findAllByPlatform(Platform.IOS) } answers {
            if (readCount.getAndIncrement() == 0 && pauseFirstRead) {
                entered.countDown()
                release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                oldList()
            } else if (readCount.get() == 1) {
                oldList()
            } else {
                newList()
            }
        }
    }

    fun startThread(block: () -> Unit): Thread {
        val thread = Thread {
            try {
                block()
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }
        thread.isDaemon = true
        thread.start()
        return thread
    }

    fun awaitFinishedOrBlocked(thread: Thread): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (System.nanoTime() < deadline) {
            val state = thread.state
            if (state == Thread.State.TERMINATED || state == Thread.State.BLOCKED || state == Thread.State.WAITING) {
                return true
            }
            Thread.sleep(POLL_MILLIS)
        }
        return false
    }

    fun join(thread: Thread) {
        thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
        (thread.isAlive) shouldBe false
    }

    private fun oldList() = listOf(version(OLD_VERSION, forceUpdate = false))

    private fun newList() = listOf(
        version(OLD_VERSION, forceUpdate = false),
        version(NEW_VERSION, forceUpdate = true),
    )

    private fun version(v: String, forceUpdate: Boolean) = AppVersion(
        platform = Platform.IOS,
        version = v,
        forceUpdate = forceUpdate,
        message = null,
        storeReleased = true,
    )
}
