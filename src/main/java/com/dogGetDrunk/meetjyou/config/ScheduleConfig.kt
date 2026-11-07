package com.dogGetDrunk.meetjyou.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * Boot does not create its own taskScheduler here because the WebSocket messageBrokerTaskScheduler
 * already exists, so @Scheduled tasks would otherwise run on the broker scheduler. A dedicated
 * scheduler is registered explicitly so scheduled-task errors reach [ScheduledTaskErrorHandler].
 */
@Configuration
@EnableScheduling
class ScheduleConfig(
    private val scheduledTaskErrorHandler: ScheduledTaskErrorHandler,
) : SchedulingConfigurer {

    @Bean
    fun scheduledTaskScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = POOL_SIZE
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX)
        scheduler.setErrorHandler(scheduledTaskErrorHandler)
        return scheduler
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setTaskScheduler(scheduledTaskScheduler())
    }

    private companion object {
        // Same thread count the scheduled tasks had on the broker scheduler in production.
        const val POOL_SIZE = 2
        const val THREAD_NAME_PREFIX = "scheduled-"
    }
}
