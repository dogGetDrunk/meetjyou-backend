package com.dogGetDrunk.meetjyou.common.exception;

import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException;
import com.dogGetDrunk.meetjyou.common.exception.business.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateException.class)
    protected ResponseEntity<ErrorResponse> handleDuplicateException(DuplicateException e) {
        log.info("Handle DuplicateException", e);

        ErrorCode errorCode = e.getErrorCode();
        String value = e.getValue();
        int status = HttpStatus.CONFLICT.value();
        ErrorResponse errorResponse = new ErrorResponse(status, errorCode, value);

        return new ResponseEntity<>(errorResponse, HttpStatus.valueOf(status));
    }

    @ExceptionHandler(NotFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNotExistException(NotFoundException e) {
        log.info("Handle NotExistException", e);

        ErrorCode errorCode = e.getErrorCode();
        String value = e.getValue();
        int status = HttpStatus.NOT_FOUND.value();
        ErrorResponse errorResponse = new ErrorResponse(status, errorCode, value);

        return new ResponseEntity<>(errorResponse, HttpStatus.valueOf(status));
    }
}
