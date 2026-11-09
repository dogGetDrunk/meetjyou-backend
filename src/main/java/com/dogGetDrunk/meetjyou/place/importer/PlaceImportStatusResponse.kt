package com.dogGetDrunk.meetjyou.place.importer

import java.time.Instant

enum class PlaceImportState {
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED,
}

/** The latest import run of this process (kept in memory; a restart shows IDLE). */
data class PlaceImportStatusResponse(
    val state: PlaceImportState,
    val dataName: String? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val read: Long = 0,
    val staged: Long = 0,
    val skipped: Long = 0,
    val inserted: Long = 0,
    val updated: Long = 0,
    val rematched: Long = 0,
    val deactivated: Long = 0,
    val failure: String? = null,
) {
    companion object {
        val IDLE = PlaceImportStatusResponse(PlaceImportState.IDLE)

        fun running(startedAt: Instant) = PlaceImportStatusResponse(PlaceImportState.RUNNING, startedAt = startedAt)

        fun failed(startedAt: Instant?, failure: String) = PlaceImportStatusResponse(
            state = PlaceImportState.FAILED,
            startedAt = startedAt,
            finishedAt = Instant.now(),
            failure = failure,
        )

        fun succeeded(startedAt: Instant?, run: ImportRun, counts: ApplyCounts) = PlaceImportStatusResponse(
            state = PlaceImportState.SUCCEEDED,
            dataName = run.file.dataName,
            startedAt = startedAt,
            finishedAt = Instant.now(),
            read = run.stats.read,
            staged = run.staged,
            skipped = run.stats.skipped,
            inserted = counts.inserted,
            updated = counts.updated,
            rematched = counts.rematched,
            deactivated = counts.deactivated,
        )
    }
}

/** What the read phase produced, before applying. */
data class ImportRun(
    val file: SbizFileRef,
    val stats: ReadStats,
    val staged: Long,
)
