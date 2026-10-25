package com.dogGetDrunk.meetjyou.common.exception

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException
import jakarta.servlet.http.HttpServletRequest
import org.apache.catalina.connector.ClientAbortException
import org.slf4j.LoggerFactory
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Extends ResponseEntityExceptionHandler so every built-in Spring MVC exception
 * (HttpMessageNotReadableException, MissingServletRequestParameterException,
 * HttpRequestMethodNotSupportedException, etc. - see handleExceptionInternal below)
 * is mapped to the correct status through a single override, instead of requiring a
 * new @ExceptionHandler each time one is found leaking through as a 500.
 */
@ControllerAdvice
class GlobalExceptionHandler(
    private val discordAlertService: DiscordAlertService
) : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    public override fun handleMethodArgumentNotValid(
        e: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        log.info("Handling MethodArgumentNotValidException.", e)

        val values = e.bindingResult.fieldErrors.map { error ->
            "[${error.field}] ${error.defaultMessage}"
        }

        alert(request, HttpStatus.BAD_REQUEST.value(), e.javaClass.simpleName, "Validation failed", values.joinToString("\n"))

        val httpStatus = HttpStatus.BAD_REQUEST
        val errorResponse = ErrorResponse(ErrorCode.INVALID_INPUT_VALUE, values)
        return ResponseEntity(errorResponse, httpStatus)
    }

    /**
     * Every domain exception: the status comes from its [ErrorCode], so adding an exception type
     * or changing a status never needs a new handler here.
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        e: BusinessException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val errorCode = e.errorCode
        if (errorCode.status.is5xxServerError) {
            log.error("Handling {}.", e.javaClass.simpleName, e)
        } else {
            log.info("Handling {}.", e.javaClass.simpleName, e)
        }

        discordAlertService.sendAlert(
            request = request,
            status = errorCode.status.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "[${errorCode.name}] ${errorCode.message}",
            detail = e.value
        )

        return ResponseEntity(ErrorResponse(errorCode, e.value), errorCode.status)
    }

    /**
     * Provider id_tokens decoded by Spring's NimbusJwtDecoder (Kakao) fail with Spring Security's
     * exceptions rather than our CustomJwtException. Only BadJwtException (expired, malformed,
     * bad signature, failed claim validation) is the client's fault and maps to 401. Its parent
     * JwtException also covers provider-side failures such as an unreachable JWK set; those stay
     * on the 500 catch-all so an outage is not reported to clients as a bad token.
     */
    @ExceptionHandler(BadJwtException::class)
    fun handleProviderJwtException(
        e: BadJwtException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.info("Handling provider BadJwtException.", e)

        discordAlertService.sendAlert(
            request = request,
            status = HttpStatus.UNAUTHORIZED.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "[${ErrorCode.INVALID_JWT.name}] ${ErrorCode.INVALID_JWT.message}",
            detail = null
        )

        val status = HttpStatus.UNAUTHORIZED
        return ResponseEntity(ErrorResponse(ErrorCode.INVALID_JWT), status)
    }

    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleSpringAuthorizationDeniedException(
        e: AuthorizationDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.info("Handling AuthorizationDeniedException.", e)

        discordAlertService.sendAlert(
            request = request,
            status = HttpStatus.FORBIDDEN.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "[${ErrorCode.ACCESS_DENIED.name}] ${ErrorCode.ACCESS_DENIED.message}",
            detail = null,
        )

        val status = HttpStatus.FORBIDDEN
        val errorResponse = ErrorResponse(ErrorCode.ACCESS_DENIED)
        return ResponseEntity(errorResponse, status)
    }

    /**
     * A row-lock deadlock (MySQL 1213) or lock wait timeout (1205), translated by Spring into a
     * PessimisticLockingFailureException subtype. Rare, unnatural request overlaps (#165) are
     * accepted instead of restructuring lock order: the transaction is fully rolled back and state
     * changes are retry-idempotent, so the client can simply retry. Still alerted, so the
     * frequency stays visible if the "rare" assumption needs revisiting.
     */
    @ExceptionHandler(PessimisticLockingFailureException::class)
    fun handlePessimisticLockingFailure(
        e: PessimisticLockingFailureException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Handling PessimisticLockingFailureException.", e)

        val errorCode = ErrorCode.CONCURRENT_REQUEST_CONFLICT
        discordAlertService.sendObservationAlert(
            request = request,
            status = HttpStatus.CONFLICT.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "[${errorCode.name}] ${errorCode.message}",
            detail = e.mostSpecificCause.message,
        )

        val status = HttpStatus.CONFLICT
        return ResponseEntity(ErrorResponse(errorCode), status)
    }

    public override fun handleNoResourceFoundException(
        e: NoResourceFoundException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val httpStatus = HttpStatus.NOT_FOUND
        return ResponseEntity(ErrorResponse(ErrorCode.NOT_FOUND), httpStatus)
    }

    /**
     * The client closed the connection before the response was fully written, so the socket is already
     * gone: returning a body would only fail again. This is a client-side disconnect, not a server
     * fault — no Discord alert, no ERROR log.
     */
    @ExceptionHandler(ClientAbortException::class)
    fun handleClientAbortException(e: ClientAbortException, request: HttpServletRequest) {
        logClientDisconnect(request, e)
    }

    /**
     * AsyncRequestNotUsableException is one of the exception types ResponseEntityExceptionHandler
     * already dispatches via its inherited handleException(), so it must be overridden here rather
     * than declared as a new @ExceptionHandler - doing both would register two candidate methods for
     * the same exception type and fail with an "Ambiguous @ExceptionHandler method mapped" error.
     */
    public override fun handleAsyncRequestNotUsableException(e: AsyncRequestNotUsableException, request: WebRequest): ResponseEntity<Any>? {
        logClientDisconnect(request.asServletRequest(), e)
        return null
    }

    private fun logClientDisconnect(request: HttpServletRequest, e: Exception) {
        log.warn(
            "Client disconnected before the response was fully written: {} {} ({})",
            request.method,
            request.requestURI,
            e.javaClass.simpleName
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        e: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Handling unexpected Exception.", e)

        val stackTrace = e.stackTrace.take(5)
            .joinToString("\n") { "  at $it" }
            .take(990)
        val detail = buildString {
            if (!e.message.isNullOrBlank()) append(e.message)
            if (stackTrace.isNotBlank()) {
                append("\n```\n")
                append(stackTrace)
                append("\n```")
            }
        }.take(1024)

        discordAlertService.sendAlert(
            request = request,
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            exceptionClass = e.javaClass.name,
            summary = e.message ?: "Unexpected error with no message",
            detail = detail.ifBlank { null }
        )

        val status = HttpStatus.INTERNAL_SERVER_ERROR
        val errorResponse = ErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR)
        return ResponseEntity(errorResponse, status)
    }

    /**
     * Single interception point for every built-in Spring MVC exception not given a dedicated
     * override above - HttpMessageNotReadableException, MissingServletRequestParameterException,
     * MethodArgumentTypeMismatchException, HttpRequestMethodNotSupportedException,
     * HttpMediaTypeNotSupportedException, NoHandlerFoundException, MaxUploadSizeExceededException,
     * and the rest of the ~20 types listed in ResponseEntityExceptionHandler#handleException.
     * Maps them all to their correct status code (typically 400) instead of letting them fall
     * through to the catch-all Exception handler as a 500.
     */
    public override fun handleExceptionInternal(
        e: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val httpStatus = HttpStatus.valueOf(statusCode.value())
        log.info("Handling built-in Spring MVC exception: {}", e.javaClass.simpleName, e)

        alert(request, httpStatus.value(), e.javaClass.simpleName, e.message ?: httpStatus.reasonPhrase, null)

        // e.message can carry parser internals (DTO class names, field paths); it stays in the log only.
        val errorResponse = ErrorResponse(
            httpStatus.value(),
            ErrorCode.INVALID_INPUT_VALUE.name,
            httpStatus.reasonPhrase,
        )
        return ResponseEntity(errorResponse, httpStatus)
    }

    private fun alert(request: WebRequest, status: Int, exceptionClass: String, summary: String, detail: String?) {
        discordAlertService.sendAlert(
            request = request.asServletRequest(),
            status = status,
            exceptionClass = exceptionClass,
            summary = summary,
            detail = detail
        )
    }

    private fun WebRequest.asServletRequest(): HttpServletRequest = (this as ServletWebRequest).request
}
