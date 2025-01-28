package com.dogGetDrunk.meetjyou.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Business
    DUPLICATE("Duplicate value"),
    NOT_FOUND("Value not found"),

    // User
    DUPLICATE_EMAIL("Duplicate email"),
    NOT_FOUND_EMAIL("Email not found");

//    private final HttpStatus status;
    private final String message;
}
