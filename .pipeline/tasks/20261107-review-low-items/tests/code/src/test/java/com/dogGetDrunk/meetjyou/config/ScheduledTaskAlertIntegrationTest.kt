package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.user.WithdrawnUserPurgeScheduler
import com.dogGetDrunk.meetjyou.user.WithdrawnUserPurgeService
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * AT-003-2: runs the real `@Scheduled` scheduler path of the application context and checks that a
 * failing scheduled task reaches the operational Discord alert while the scheduler keeps working.
 * Other scheduled jobs may call the same mock, so alerts are matched by argument, never by count.
 */
@SpringBootTest
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class ScheduledTaskAlertIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var postProcessor: ScheduledAnnotationBeanPostProcessor

    @Autowired
    private lateinit var purgeScheduler: WithdrawnUserPurgeScheduler

    @MockBean
    private lateinit var discordAlertService: DiscordAlertService

    @MockBean
    private lateinit var purgeService: WithdrawnUserPurgeService

    private val alerts = CopyOnWriteArrayList<RecordedAlert>()

    private data class RecordedAlert(val source: String, val exceptionClass: String, val summary: String)

    // Bean looked up by name so the file compiles on the base commit, where the bean does not exist.
    private fun scheduledTaskScheduler(): ThreadPoolTaskScheduler =
        context.getBean(SCHEDULED_SCHEDULER_BEAN, ThreadPoolTaskScheduler::class.java)

    private fun registeredScheduler(): Any? {
        val registrar = ReflectionTestUtils.getField(postProcessor, "registrar") as ScheduledTaskRegistrar
        return registrar.scheduler
    }

    private fun recordAlerts() {
        alerts.clear()
        doAnswer { invocation ->
            alerts.add(
                RecordedAlert(
                    source = invocation.getArgument(0),
                    exceptionClass = invocation.getArgument(1),
                    summary = invocation.getArgument(2),
                )
            )
            null
        }.`when`(discordAlertService).sendOperationalAlert(anyString(), anyString(), anyString(), any())
    }

    private fun awaitAlert(exceptionClass: String, messagePart: String): RecordedAlert? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS)
        while (System.nanoTime() < deadline) {
            alerts.firstOrNull { it.exceptionClass == exceptionClass && it.summary.contains(messagePart) }
                ?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        return null
    }

    private fun anyInstant(): Instant = any(Instant::class.java) ?: Instant.EPOCH

    init {
        extensions(SpringExtension())

        beforeEach { recordAlerts() }

        // AT-003-2 step 1
        given("AT-003-2: 컨텍스트의 @Scheduled 처리기가") {
            `when`("작업 등록에 쓰는 스케줄러를 꺼내면") {
                then("scheduledTaskScheduler 빈과 같은 인스턴스이고 messageBrokerTaskScheduler와는 다르다") {
                    val registered = registeredScheduler()

                    registered shouldNotBe null
                    (registered === scheduledTaskScheduler()) shouldBe true
                    val broker = context.getBean(BROKER_SCHEDULER_BEAN)
                    (registered === broker) shouldBe false
                }
            }
        }

        // AT-003-2 step 2
        given("AT-003-2: scheduledTaskScheduler에서 실행한 작업이 예외를 던지면") {
            `when`("이어서 같은 스케줄러로 정상 작업을 실행할 때") {
                then("운영 알림이 나가고 스케줄러는 계속 동작한다") {
                    val message = "boom-${UUID.randomUUID()}"
                    val scheduler = scheduledTaskScheduler()
                    val next = CountDownLatch(1)

                    scheduler.schedule({ throw IllegalStateException(message) }, Instant.now())

                    val alert = awaitAlert("IllegalStateException", message)
                    alert shouldNotBe null
                    alert?.summary?.shouldContain(message)
                    scheduler.schedule({ next.countDown() }, Instant.now())
                    next.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) shouldBe true
                }
            }
        }

        // AT-003-2 step 3
        given("AT-003-2: 탈퇴 유저 purge가 예외를 던지도록 stub하고") {
            `when`("purgeExpiredWithdrawals를 scheduledTaskScheduler로 실행하면") {
                then("주입한 예외 클래스와 메시지로 운영 알림이 나간다") {
                    val message = "purge-boom-${UUID.randomUUID()}"
                    val target = UserFixtures.user(nickname = "purgeAt3")
                    `when`(purgeService.findPurgeTargets(anyInstant())).thenReturn(listOf(target))
                    `when`(purgeService.purge(anyCollection<Long>(), anyInstant()))
                        .thenThrow(PurgeFailure(message))

                    scheduledTaskScheduler().schedule(
                        { purgeScheduler.purgeExpiredWithdrawals() },
                        Instant.now(),
                    )

                    val alert = awaitAlert(PurgeFailure::class.java.simpleName, message)
                    alert shouldNotBe null
                    alert?.summary?.shouldContain(message)
                }
            }
        }
    }

    private class PurgeFailure(message: String) : RuntimeException(message)

    private companion object {
        const val SCHEDULED_SCHEDULER_BEAN = "scheduledTaskScheduler"
        const val BROKER_SCHEDULER_BEAN = "messageBrokerTaskScheduler"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
    }
}
