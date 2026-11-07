package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify

/**
 * AT-003-1: ScheduledTaskErrorHandler sends exactly one operational alert, never rethrows,
 * and derives `source` from the first application-package stack frame.
 */
class ScheduledTaskErrorHandlerTest : BehaviorSpec() {

    private val discordAlertService = mockk<DiscordAlertService>(relaxed = true)
    private val sut = ScheduledTaskErrorHandler(discordAlertService)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        given("AT-003-1 first app frame is WithdrawnUserPurgeScheduler.purgeExpiredWithdrawals") {
            val error = IllegalStateException(BOOM_MESSAGE)
            error.stackTrace = arrayOf(
                frame("java.lang.Thread", "run"),
                frame(PURGE_SCHEDULER_CLASS, "purgeExpiredWithdrawals"),
                frame("org.springframework.scheduling.support.ScheduledMethodRunnable", "run"),
            )

            `when`("handleError is called") {
                then("it does not throw and sends exactly one alert with the derived source") {
                    shouldNotThrowAny { sut.handleError(error) }

                    verify(exactly = 1) {
                        discordAlertService.sendOperationalAlert(
                            source = "ScheduledTask:WithdrawnUserPurgeScheduler.purgeExpiredWithdrawals",
                            exceptionClass = "IllegalStateException",
                            summary = match { it.contains(BOOM_MESSAGE) },
                            detail = any(),
                        )
                    }
                }
            }
        }

        given("AT-003-1 an exception with no application-package frame") {
            val error = RuntimeException("x")
            error.stackTrace = arrayOf(
                frame("java.lang.Thread", "run"),
                frame("java.util.concurrent.FutureTask", "run"),
                frame("org.springframework.scheduling.support.ScheduledMethodRunnable", "run"),
            )

            `when`("handleError is called") {
                then("it does not throw and sends one alert with the bare ScheduledTask source") {
                    shouldNotThrowAny { sut.handleError(error) }

                    verify(exactly = 1) {
                        discordAlertService.sendOperationalAlert(
                            source = "ScheduledTask",
                            exceptionClass = "RuntimeException",
                            summary = any(),
                            detail = any(),
                        )
                    }
                }
            }
        }
    }

    private fun frame(className: String, method: String) = StackTraceElement(className, method, null, -1)

    companion object {
        private const val BOOM_MESSAGE = "boom"
        private const val PURGE_SCHEDULER_CLASS = "com.dogGetDrunk.meetjyou.user.WithdrawnUserPurgeScheduler"
    }
}
