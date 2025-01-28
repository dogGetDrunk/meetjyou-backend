package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    // https://dukcode.github.io/spring/spring-custom-exception-and-exception-strategy/
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
