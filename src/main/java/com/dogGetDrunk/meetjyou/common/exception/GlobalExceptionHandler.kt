package com.dogGetDrunk.meetjyou.common.exception

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.auth.AuthException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException

@ControllerAdvice
class GlobalExceptionHandler(
    private val discordAlertService: DiscordAlertService
) {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        e: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.info("Handling MethodArgumentNotValidException.", e)

        val values = e.bindingResult.fieldErrors.map { error ->
            "[${error.field}] ${error.defaultMessage}"
        }

        discordAlertService.sendAlert(
            request = request,
            status = HttpStatus.BAD_REQUEST.value(),
            exceptionClass = e.javaClass.simpleName,
            summary = "Validation failed",
            detail = values.joinToString("\n")
        )

        val status = HttpStatus.BAD_REQUEST
        val errorResponse = ErrorResponse(status.value(), ErrorCode.INVALID_INPUT_VALUE, values)
        return ResponseEntity(errorResponse, status)
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

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(): ResponseEntity<ErrorResponse> {
        val status = HttpStatus.NOT_FOUND
        return ResponseEntity(ErrorResponse(status.value(), ErrorCode.NOT_FOUND), status)
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
}
