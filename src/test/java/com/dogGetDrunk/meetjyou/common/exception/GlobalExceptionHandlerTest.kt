package com.dogGetDrunk.meetjyou.common.exception

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.common.exception.business.auth.UnauthenticatedException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomExpiredJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PostNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyFullException
import com.dogGetDrunk.meetjyou.common.exception.business.post.PostUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.user.DuplicateNicknameException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import java.io.IOException
import java.sql.SQLTransactionRollbackException
import java.util.UUID
import org.apache.catalina.connector.ClientAbortException
import org.springframework.dao.CannotAcquireLockException
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

    given("기반 클래스마다 다른 도메인 예외가 발생하면") {
        val uuid = UUID.randomUUID()
        val cases = listOf(
            PartyFullException(uuid) to HttpStatus.BAD_REQUEST,
            PostNotFoundException(uuid) to HttpStatus.NOT_FOUND,
            DuplicateNicknameException("dup") to HttpStatus.CONFLICT,
            PostUpdateAccessDeniedException(uuid, uuid) to HttpStatus.FORBIDDEN,
            UnauthenticatedException(uuid.toString()) to HttpStatus.UNAUTHORIZED,
            CustomExpiredJwtException(null) to HttpStatus.UNAUTHORIZED,
        )

        `when`("예외 해석기가 핸들러를 고르면") {
            then("모두 단일 BusinessException 핸들러로 라우팅된다") {
                val resolver = ExceptionHandlerMethodResolver(GlobalExceptionHandler::class.java)

                cases.forEach { (exception, _) ->
                    (exception::class.simpleName to resolver.resolveMethodByThrowable(exception)?.name) shouldBe
                        (exception::class.simpleName to "handleBusinessException")
                }
            }
        }

        `when`("handleBusinessException을 호출하면") {
            then("기반 클래스별 기존 상태와 같은 상태·본문으로 응답한다") {
                cases.forEach { (exception, expected) ->
                    val response = sut.handleBusinessException(exception, request)

                    response.statusCode shouldBe expected
                    response.body?.status shouldBe expected.value()
                    response.body?.errorCode shouldBe exception.errorCode.name
                }
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

            then("파싱 내부 정보(클래스명·필드 경로)를 응답 메시지에 노출하지 않는다") {
                val internalDetail = "JSON parse error: Cannot deserialize value of type `${CLASS_IN_PARSE_ERROR}`"
                val exception = HttpMessageNotReadableException(internalDetail)

                val response = sut.handleExceptionInternal(exception, null, HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest)

                (response.body as ErrorResponse).message shouldBe HttpStatus.BAD_REQUEST.reasonPhrase
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

    given("두 트랜잭션이 역순으로 행을 잠가 MySQL이 교착으로 한쪽을 롤백하면") {
        // Same exception chain the dockerTest deadlock scenarios produce (MySQL 1213, SQLState 40001).
        val cause = SQLTransactionRollbackException(
            "Deadlock found when trying to get lock; try restarting transaction",
            "40001",
            1213,
        )
        val exception = CannotAcquireLockException("could not execute statement", cause)

        `when`("handlePessimisticLockingFailure를 호출하면") {
            then("500이 아닌 409와 CONCURRENT_REQUEST_CONFLICT를 반환하고, 빈도 관측용 알림은 보낸다") {
                clearMocks(discordAlertService)

                val response = sut.handlePessimisticLockingFailure(exception, request)

                response.statusCode shouldBe HttpStatus.CONFLICT
                response.body?.errorCode shouldBe ErrorCode.CONCURRENT_REQUEST_CONFLICT.name
                verify(exactly = 1) {
                    discordAlertService.sendObservationAlert(any(), HttpStatus.CONFLICT.value(), any(), any(), any())
                }
            }
        }

        `when`("예외 해석기가 핸들러를 고르면") {
            then("catch-all 500 핸들러가 아니라 교착 전용 핸들러로 라우팅된다") {
                val resolver = ExceptionHandlerMethodResolver(GlobalExceptionHandler::class.java)

                resolver.resolveMethodByThrowable(exception)?.name shouldBe "handlePessimisticLockingFailure"
            }
        }
    }
})

private const val CLASS_IN_PARSE_ERROR = "com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest"
