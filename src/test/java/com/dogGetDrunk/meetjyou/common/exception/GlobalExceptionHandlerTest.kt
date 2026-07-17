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
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver
import org.springframework.web.servlet.resource.NoResourceFoundException

class GlobalExceptionHandlerTest : BehaviorSpec({

    val discordAlertService = mockk<DiscordAlertService>(relaxed = true)
    val request = mockk<HttpServletRequest>(relaxed = true)
    val webRequest = ServletWebRequest(request)
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

    given("요청 본문이 파싱 불가능한 JSON이면") {
        `when`("HttpMessageNotReadableException이 발생하면") {
            then("500이 아닌 400을 반환한다") {
                val exception = HttpMessageNotReadableException("JSON parse error")

                val response = sut.handleExceptionInternal(exception, null, HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest)

                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                (response.body as ErrorResponse).errorCode shouldBe ErrorCode.INVALID_INPUT_VALUE.name
            }
        }
    }

    given("존재하지 않는 정적 리소스를 요청하면") {
        `when`("NoResourceFoundException이 발생하면") {
            then("404를 반환하고 Discord 알림을 보내지 않는다") {
                val exception = NoResourceFoundException(HttpMethod.GET, "/no-such-file")
                clearMocks(discordAlertService)

                val response = sut.handleNoResourceFoundException(exception, HttpHeaders(), HttpStatus.NOT_FOUND, webRequest)

                response.statusCode shouldBe HttpStatus.NOT_FOUND
                verify(exactly = 0) { discordAlertService.sendAlert(any(), any(), any(), any(), any()) }
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

                sut.handleAsyncRequestNotUsableException(exception, webRequest)

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
            then("클라이언트 이탈 전용 핸들러가 선택된다") {
                val resolver = ExceptionHandlerMethodResolver(GlobalExceptionHandler::class.java)

                val method = resolver.resolveMethod(ClientAbortException(IOException("Broken pipe")))

                method?.name shouldBe "handleClientAbortException"
            }
        }
    }
})
