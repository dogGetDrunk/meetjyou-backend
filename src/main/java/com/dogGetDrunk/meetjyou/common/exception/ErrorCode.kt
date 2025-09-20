package com.dogGetDrunk.meetjyou.common.exception

enum class ErrorCode(
    val message: String
) {
    // Business
    DUPLICATE("Duplicate value"),
    NOT_FOUND("Value not found"),
    INVALID_INPUT_VALUE("Invalid input value"),

    // User
    DUPLICATE_EMAIL("Duplicate email"),
    DUPLICATE_NICKNAME("Duplicate nickname"),
    EMAIL_NOT_FOUND("Email not found"),
    USER_NOT_FOUND("User not found"),
    INVALID_NICKNAME("Invalid nickname"),
    TOO_LONG_BIO("Bio is too long"),

    // JWT
    TOKEN_COMMON("The token has an issue"),
    EXPIRED_TOKEN("The token has expired"),
    INCORRECT_TOKEN_SUBJECT("The subject in the token is incorrect"),
    MISSING_AUTHORIZATION_HEADER("This authorization header is invalid"),
    INVALID_ACCESS_TOKEN("The access token is invalid"),

    // Auth
    AUTHENTICATION_FAILED("Authentication failed"),
    UNAUTHORIZED("Unauthorized"),
    UNAUTHENTICATED("Unauthenticated"),

    // Version
    VERSION_NOT_FOUND("Version not found"),

    // Notice
    NOTICE_NOT_FOUND("Notice not found"),

    // Post
    POST_NOT_FOUND("Post not found"),

    // Plan
    PLAN_NOT_FOUND("Plan not found"),

    // Party
    PARTY_NOT_FOUND("Party not found"),

    // Image
    IMAGE_UPLOAD_FAILED("Image upload failed"),

    // ChatRoom
    CHAT_ROOM_NOT_FOUND("Chat room not found"),

    // Push tokne
    PUSH_TOKEN_NOT_FOUND("Push token not found"),
}
