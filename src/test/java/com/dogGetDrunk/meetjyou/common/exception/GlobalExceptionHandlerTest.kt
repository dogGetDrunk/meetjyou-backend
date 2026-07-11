package com.dogGetDrunk.meetjyou.common.exception

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.security.authorization.AuthorizationResult

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
})
