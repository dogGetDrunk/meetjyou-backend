package com.dogGetDrunk.meetjyou.common.exception

enum class ErrorCode(
    val message: String
) {
    // Business
    DUPLICATE("Duplicate value"),
    NOT_FOUND("Value not found"),

    // User
    DUPLICATE_EMAIL("Duplicate email"),
    EMAIL_NOT_FOUND("Email not found"),
    USER_NOT_FOUND("User not found"),

    // JWT
    TOKEN_COMMON("The token has an issue."),
    EXPIRED_TOKEN("The token has expired."),
    INCORRECT_TOKEN_SUBJECT("The subject in the token is incorrect."),
    INVALID_AUTHORIZATION_HEADER("This authorization header is invalid"),

    // Version
    VERSION_NOT_FOUND("Version not found");
}
