package com.dogGetDrunk.meetjyou.place.importer

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.common.exception.business.place.PlaceImportInProgressException
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one SBIZ import at a time in the background (download → filter → stage → validate → apply).
 * The guard is per process, which is enough while the app runs on a single instance.
 */
@Service
class PlaceImportService(
    private val fileSource: SbizFileSource,
    private val zipReader: SbizZipReader,
    private val writer: PlaceImportWriter,
    private val props: PlaceImportProperties,
    private val discordAlertService: DiscordAlertService,
) {
    private val log = LoggerFactory.getLogger(PlaceImportService::class.java)
    private val running = AtomicBoolean(false)

    // Not a Spring bean on purpose: any Executor bean switches off Boot's applicationTaskExecutor.
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, THREAD_NAME).apply { isDaemon = true }
    }

    @Volatile
    private var status: PlaceImportStatusResponse = PlaceImportStatusResponse.IDLE

    fun start(): PlaceImportStatusResponse {
        if (!running.compareAndSet(false, true)) throw PlaceImportInProgressException()
        val started = PlaceImportStatusResponse.running(Instant.now())
        status = started
        executor.execute { runGuarded(started.startedAt) }
        log.info("Place import started")
        return started
    }

    fun latest(): PlaceImportStatusResponse = status

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    // A background run has no caller to propagate to: any failure must end as FAILED + alert.
    @Suppress("TooGenericExceptionCaught")
    private fun runGuarded(startedAt: Instant?) {
        try {
            status = runImport(startedAt)
            log.info("Place import succeeded: {}", status)
            discordAlertService.sendOperationalAlert(
                ALERT_SOURCE, SUCCESS_CLASS, "Place import succeeded", status.toString(),
            )
        } catch (e: Exception) {
            status = PlaceImportStatusResponse.failed(startedAt, "${e::class.simpleName}: ${e.message}")
            log.error("Place import failed", e)
            discordAlertService.sendOperationalAlert(
                ALERT_SOURCE, e::class.simpleName.orEmpty(), "Place import failed", e.message,
            )
        } finally {
            running.set(false)
        }
    }

    private fun runImport(startedAt: Instant?): PlaceImportStatusResponse {
        writer.clearStaging()
        val run = fileSource.download { file, body ->
            val stats = zipReader.read(body, props.stagingBatchSize, writer::stage)
            ImportRun(file, stats, writer.stagedCount())
        }
        log.info("Place import staged {} of {} rows from {}", run.staged, run.stats.read, run.file.dataName)
        writer.validateCompleteness()
        return PlaceImportStatusResponse.succeeded(startedAt, run, writer.apply())
    }

    companion object {
        const val ALERT_SOURCE = "place-import"
        private const val SUCCESS_CLASS = "PlaceImportSucceeded"
        private const val THREAD_NAME = "place-import"
    }
}
