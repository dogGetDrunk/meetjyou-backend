package com.dogGetDrunk.meetjyou.common.exception

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.auth.AuthException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.apache.catalina.connector.ClientAbortException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.authorization.AuthorizationDeniedException
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
        val errorResponse = ErrorResponse(httpStatus.value(), ErrorCode.INVALID_INPUT_VALUE, values)
        return ResponseEntity(errorResponse, httpStatus)
    }

    @ExceptionHandler(DuplicateException::class)
    fun handleDuplicateException(
        e: DuplicateException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.info("Handling DuplicateException.", e)

        discordAlertService.sendAlert(
            request = request,
            status = HttpStatus.CONFLICT.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "[${e.errorCode.name}] ${e.errorCode.message}",
            detail = e.value
        )

        val status = HttpStatus.CONFLICT
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotExistException(
        e: NotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.info("Handling NotFoundException.", e)

        discordAlertService.sendAlert(
            request = request,
            status = HttpStatus.NOT_FOUND.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "[${e.errorCode.name}] ${e.errorCode.message}",
            detail = e.value
        )

        val status = HttpStatus.NOT_FOUND
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(CustomJwtException::class)
    fun handleCustomJwtException(
        e: CustomJwtException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.info("Handling CustomJwtException.", e)

        discordAlertService.sendAlert(
            request = request,
            status = HttpStatus.UNAUTHORIZED.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "[${e.errorCode.name}] ${e.errorCode.message}",
            detail = e.value
        )

        val status = HttpStatus.UNAUTHORIZED
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(AuthException::class)
    fun handleAuthException(
        e: AuthException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.info("Handling AuthException.", e)

        discordAlertService.sendAlert(
            request = request,
            status = HttpStatus.UNAUTHORIZED.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "[${e.errorCode.name}] ${e.errorCode.message}",
            detail = e.value
        )

        val status = HttpStatus.UNAUTHORIZED
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(InvalidInputException::class)
    fun handleInvalidInputException(
        e: InvalidInputException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.info("Handling InvalidInputException.", e)

        discordAlertService.sendAlert(
            request = request,
            status = HttpStatus.BAD_REQUEST.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "[${e.errorCode.name}] ${e.errorCode.message}",
            detail = e.value
        )

        val status = HttpStatus.BAD_REQUEST
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(
        e: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.info("Handling AccessDeniedException.", e)

        discordAlertService.sendAlert(
            request = request,
            status = HttpStatus.FORBIDDEN.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "[${e.errorCode.name}] ${e.errorCode.message}",
            detail = e.value
        )

        val status = HttpStatus.FORBIDDEN
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
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
        val errorResponse = ErrorResponse(status.value(), ErrorCode.ACCESS_DENIED)
        return ResponseEntity(errorResponse, status)
    }

    public override fun handleNoResourceFoundException(
        e: NoResourceFoundException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val httpStatus = HttpStatus.NOT_FOUND
        return ResponseEntity(ErrorResponse(httpStatus.value(), ErrorCode.NOT_FOUND), httpStatus)
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
        val errorResponse = ErrorResponse(status.value(), ErrorCode.INTERNAL_SERVER_ERROR)
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

        val message = e.message ?: httpStatus.reasonPhrase
        val errorResponse = ErrorResponse(httpStatus.value(), ErrorCode.INVALID_INPUT_VALUE.name, message)
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
