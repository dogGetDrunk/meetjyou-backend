package com.dogGetDrunk.meetjyou.common.discord

import com.dogGetDrunk.meetjyou.config.DiscordProperties
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import java.net.InetSocketAddress
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
})
