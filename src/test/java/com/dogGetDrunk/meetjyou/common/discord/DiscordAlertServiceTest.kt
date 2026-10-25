package com.dogGetDrunk.meetjyou.common.discord

import com.dogGetDrunk.meetjyou.common.exception.GlobalExceptionHandler
import com.dogGetDrunk.meetjyou.config.DiscordProperties
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.springframework.dao.CannotAcquireLockException
import java.net.InetSocketAddress
import java.sql.SQLTransactionRollbackException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class DiscordAlertServiceTest : BehaviorSpec({

    val receivedCount = AtomicInteger(0)
    val server = HttpServer.create(InetSocketAddress(0), 0)
    server.createContext("/webhook") { exchange ->
        receivedCount.incrementAndGet()
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
    }
    server.start()
    afterSpec { server.stop(0) }

    val sut = DiscordAlertService(DiscordProperties(webhookUrl = "http://localhost:${server.address.port}/webhook"))
    val request = mockk<HttpServletRequest>()
    every { request.method } returns "GET"
    every { request.requestURI } returns "/api/test"

    given("4xx 상태로 알림을 보내면") {
        `when`("sendAlert를 호출하면") {
            then("Discord로 전송하지 않는다") {
                receivedCount.set(0)

                sut.sendAlert(request, 400, "InvalidInputException", "bad request")

                Thread.sleep(300)
                receivedCount.get() shouldBe 0
            }
        }
    }

    given("5xx 상태로 알림을 보내면") {
        `when`("sendAlert를 호출하면") {
            then("Discord로 전송한다") {
                receivedCount.set(0)

                sut.sendAlert(request, 500, "RuntimeException", "unexpected error")

                eventually(2.seconds) {
                    receivedCount.get() shouldBe 1
                }
            }
        }
    }

    given("4xx라도 빈도 관측용으로 강제 전송을 요청하면") {
        `when`("sendObservationAlert를 호출하면") {
            then("Discord로 전송한다") {
                receivedCount.set(0)

                sut.sendObservationAlert(request, 409, "CannotAcquireLockException", "deadlock")

                eventually(2.seconds) {
                    receivedCount.get() shouldBe 1
                }
            }
        }
    }

    given("요청은 성공시키고 삼킨 실패를 알릴 때 (HTTP 요청 정보 없음)") {
        `when`("sendOperationalAlert를 호출하면") {
            then("요청 경로 대신 작업 이름으로 Discord에 전송한다") {
                receivedCount.set(0)

                sut.sendOperationalAlert("Apple revoke WITHDRAWAL", "AppleAuthApiException", "revoke failed")

                eventually(2.seconds) {
                    receivedCount.get() shouldBe 1
                }
            }
        }
    }

    given("행 락 교착이 409로 응답되면") {
        // Real service, not a mock: the handler test only proved sendAlert was called, while the
        // service itself dropped every status < 500, so no deadlock alert ever reached Discord.
        val handler = GlobalExceptionHandler(sut)
        val exception = CannotAcquireLockException(
            "could not execute statement",
            SQLTransactionRollbackException("Deadlock found when trying to get lock", "40001", 1213),
        )
        every { request.requestURI } returns "/api/deadlock"

        `when`("handlePessimisticLockingFailure를 호출하면") {
            then("빈도 관측용 알림이 실제로 Discord에 도착한다") {
                receivedCount.set(0)

                handler.handlePessimisticLockingFailure(exception, request)

                eventually(2.seconds) {
                    receivedCount.get() shouldBe 1
                }
            }
        }
    }
})
