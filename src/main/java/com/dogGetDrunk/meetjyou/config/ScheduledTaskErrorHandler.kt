package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.util.ErrorHandler

/**
 * Error handler for every @Scheduled task: logs, sends one operational alert, and suppresses the
 * error so a repeating task keeps its schedule. The source names the failing task, so the alert
 * dedup key (status:exceptionClass:source) is per task.
 */
@Component
class ScheduledTaskErrorHandler(
    private val discordAlertService: DiscordAlertService,
) : ErrorHandler {
    private val log = LoggerFactory.getLogger(ScheduledTaskErrorHandler::class.java)

    override fun handleError(t: Throwable) {
        val source = resolveSource(t)
        log.error("Scheduled task failed. source={}", source, t)
        discordAlertService.sendOperationalAlert(
            source = source,
            exceptionClass = t::class.java.simpleName,
            summary = buildSummary(t),
            detail = t.stackTraceToString().take(MAX_DETAIL_LENGTH),
        )
    }

    private fun resolveSource(t: Throwable): String {
        val frame = t.stackTrace.firstOrNull { it.className.startsWith(APP_PACKAGE_PREFIX) }
            ?: return SOURCE_PREFIX
        val simpleClassName = frame.className.substringAfterLast('.')
        return "$SOURCE_PREFIX:$simpleClassName.${frame.methodName}"
    }

    private fun buildSummary(t: Throwable): String {
        val message = t.message ?: t::class.java.name
        return message.take(MAX_SUMMARY_LENGTH)
    }

    private companion object {
        const val APP_PACKAGE_PREFIX = "com.dogGetDrunk.meetjyou"
        const val SOURCE_PREFIX = "ScheduledTask"
        const val MAX_SUMMARY_LENGTH = 500
        const val MAX_DETAIL_LENGTH = 1000
    }
}
