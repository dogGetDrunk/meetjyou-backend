package com.dogGetDrunk.meetjyou.common.exception

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import java.io.IOException
import org.apache.catalina.connector.ClientAbortException
import org.springframework.http.HttpStatus
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver

class GlobalExceptionHandlerTest : BehaviorSpec({

    val discordAlertService = mockk<DiscordAlertService>(relaxed = true)
    val request = mockk<HttpServletRequest>(relaxed = true)
    val sut = GlobalExceptionHandler(discordAlertService)

    given("@PreAuthorize에 의해 AuthorizationDeniedException이 발생하면") {
        `when`("handleSpringAuthorizationDeniedException을 호출하면") {
            then("500이 아닌 403과 ACCESS_DENIED 에러코드를 반환한다") {
                val exception = AuthorizationDeniedException("Access Denied", mockk<AuthorizationResult>(relaxed = true))

                val response = sut.handleSpringAuthorizationDeniedException(exception, request)

                response.statusCode shouldBe HttpStatus.FORBIDDEN
                response.body?.errorCode shouldBe ErrorCode.ACCESS_DENIED.name
            }
        }
    }

    given("클라이언트가 응답을 다 받기 전에 연결을 끊으면") {
        `when`("AsyncRequestNotUsableException이 발생하면") {
            then("500 알림을 Discord로 보내지 않는다") {
                val exception = AsyncRequestNotUsableException(
                    "ServletOutputStream failed to flush",
                    IOException("Broken pipe")
                )
                clearMocks(discordAlertService)

                sut.handleClientAbortException(exception, request)

                verify(exactly = 0) { discordAlertService.sendAlert(any(), any(), any(), any(), any()) }
            }
        }

        `when`("ClientAbortException이 발생하면") {
            then("500 알림을 Discord로 보내지 않는다") {
                clearMocks(discordAlertService)

                sut.handleClientAbortException(ClientAbortException(IOException("Broken pipe")), request)

                verify(exactly = 0) { discordAlertService.sendAlert(any(), any(), any(), any(), any()) }
            }
        }

        `when`("예외 핸들러를 조회하면") {
            then("catch-all 핸들러가 아닌 클라이언트 이탈 전용 핸들러가 선택된다") {
                val resolver = ExceptionHandlerMethodResolver(GlobalExceptionHandler::class.java)
                val exception = AsyncRequestNotUsableException(
                    "ServletOutputStream failed to flush",
                    IOException("Broken pipe")
                )

                val method = resolver.resolveMethod(exception)

                method?.name shouldBe "handleClientAbortException"
            }
        }
    }
})
