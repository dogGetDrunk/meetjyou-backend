package com.dogGetDrunk.meetjyou.place.importer

import com.dogGetDrunk.meetjyou.place.PlaceRegionStats
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.InputStream

// AT-005 (unit): the per-region counts are dropped only when an import succeeds.
class PlaceImportStatsInvalidationTest : BehaviorSpec() {
    init {
        given("적재가 성공하면") {
            then("지역별 건수 캐시를 비운다") {
                val stats = mockk<PlaceRegionStats>(relaxed = true)
                run(writer(), stats) shouldBe PlaceImportState.SUCCEEDED
                verify(exactly = 1) { stats.invalidate() }
            }
        }
        given("적재가 실패하면") {
            then("캐시를 그대로 둔다") {
                val stats = mockk<PlaceRegionStats>(relaxed = true)
                val writer = writer()
                every { writer.validateCompleteness() } throws PlaceImportException("staging is empty")
                run(writer, stats) shouldBe PlaceImportState.FAILED
                verify(exactly = 0) { stats.invalidate() }
            }
        }
    }

    // Built per leaf: a spec instance may run several leaves, and mock call records would carry over.
    private fun writer(): PlaceImportWriter = mockk(relaxed = true) {
        every { apply() } returns ApplyCounts(updated = 0, rematched = 0, inserted = 1, deactivated = 0)
    }

    private fun run(writer: PlaceImportWriter, stats: PlaceRegionStats): PlaceImportState {
        val zipReader = mockk<SbizZipReader> { every { read(any(), any(), any()) } returns ReadStats() }
        val service = PlaceImportService(
            FixtureSource, zipReader, writer, PlaceImportProperties(), mockk(relaxed = true), stats,
        )
        try {
            service.start()
            val deadline = System.currentTimeMillis() + WAIT_MILLIS
            while (service.latest().state == PlaceImportState.RUNNING) {
                check(System.currentTimeMillis() < deadline) { "Import did not finish in time" }
                Thread.sleep(POLL_MILLIS)
            }
            return service.latest().state
        } finally {
            service.shutdown()
        }
    }

    private object FixtureSource : SbizFileSource {
        override fun <T : Any> download(consumer: (SbizFileRef, InputStream) -> T): T =
            consumer(SbizFileRef("FILE_TEST", "1", "fixture"), ByteArrayInputStream(ByteArray(0)))
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
    }
}
