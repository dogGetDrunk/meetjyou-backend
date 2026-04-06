package com.dogGetDrunk.meetjyou.common.exception

import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.auth.AuthException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        log.info("Handling MethodArgumentNotValidException.", e)

        val values = e.bindingResult.fieldErrors.map { error ->
            "[${error.field}] ${error.defaultMessage}"
        }

        val status = HttpStatus.BAD_REQUEST
        val errorResponse = ErrorResponse(status.value(), ErrorCode.INVALID_INPUT_VALUE, values)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(DuplicateException::class)
    fun handleDuplicateException(e: DuplicateException): ResponseEntity<ErrorResponse> {
        log.info("Handling DuplicateException.", e)

        val status = HttpStatus.CONFLICT
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotExistException(e: NotFoundException): ResponseEntity<ErrorResponse> {
        log.info("Handling NotFoundException.", e)

        val status = HttpStatus.NOT_FOUND
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(CustomJwtException::class)
    fun handleCustomJwtException(e: CustomJwtException): ResponseEntity<ErrorResponse> {
        log.info("Handling CustomJwtException.", e)

        val status = HttpStatus.UNAUTHORIZED
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(AuthException::class)
    fun handleAuthException(e: AuthException): ResponseEntity<ErrorResponse> {
        log.info("Handling AuthException.", e)

        val status = HttpStatus.UNAUTHORIZED
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(InvalidInputException::class)
    fun handleInvalidInputException(e: InvalidInputException): ResponseEntity<ErrorResponse> {
        log.info("Handling InvalidInputException.", e)

        val status = HttpStatus.BAD_REQUEST
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> {
        log.info("Handling AccessDeniedException.", e)

        val status = HttpStatus.FORBIDDEN
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }
}
