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
    NOT_FOUND_EMAIL("Email not found"),

    // JWT
    TOKEN_COMMON("The token has an issue."),
    EXPIRED_TOKEN("The token has expired."),
    INCORRECT_TOKEN_SUBJECT("The subject in the token is incorrect."),
    INVALID_AUTHORIZATION_HEADER("This authorization header is invalid");

//    private final HttpStatus status;
    private final String message;
}
