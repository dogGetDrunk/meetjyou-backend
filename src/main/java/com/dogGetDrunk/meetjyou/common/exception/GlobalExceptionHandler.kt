package com.dogGetDrunk.meetjyou.common.exception

import com.dogGetDrunk.meetjyou.common.exception.business.CustomJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException
import com.dogGetDrunk.meetjyou.common.exception.business.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(DuplicateException::class)
    fun handleDuplicateException(e: DuplicateException): ResponseEntity<ErrorResponse> {
        log.info("Handle DuplicateException", e)
        val status = HttpStatus.CONFLICT
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotExistException(e: NotFoundException): ResponseEntity<ErrorResponse> {
        log.info("Handle NotExistException", e)
        val status = HttpStatus.NOT_FOUND
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(CustomJwtException::class)
    fun handleCustomJwtException(e: CustomJwtException): ResponseEntity<ErrorResponse> {
        log.info("Handle CustomJwtException", e)
        val status = HttpStatus.UNAUTHORIZED
        val errorResponse = ErrorResponse(status.value(), e.errorCode, e.value)
        return ResponseEntity(errorResponse, status)
    }
}
