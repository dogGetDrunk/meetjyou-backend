package com.dogGetDrunk.meetjyou.common.exception

import org.springframework.http.HttpStatus

/**
 * The HTTP status lives next to each code, so changing what a failure responds with is a
 * one-line edit here instead of moving the exception under another base class.
 */
enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    // Business
    DUPLICATE(HttpStatus.CONFLICT, "Duplicate value"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Value not found"),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "Invalid input value"),

    // Request shape (Spring MVC built-ins)
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this path"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type not supported"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Request body is too large"),

    // User
    DUPLICATE_USER(HttpStatus.CONFLICT, "Duplicate email"),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "Duplicate nickname"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),

    // Social account
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "Social account is linked to another user"),
    SOCIAL_PROVIDER_ALREADY_LINKED(HttpStatus.CONFLICT, "Another account of this provider is already linked"),
    SOCIAL_ACCOUNT_NOT_LINKED(HttpStatus.NOT_FOUND, "Social account is not linked"),
    LAST_SOCIAL_ACCOUNT_UNLINK(HttpStatus.CONFLICT, "Cannot unlink the last social account"),

    // JWT
    TOKEN_COMMON(HttpStatus.UNAUTHORIZED, "The token has an issue"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "The token has expired"),
    INCORRECT_TOKEN_SUBJECT(HttpStatus.UNAUTHORIZED, "The subject in the token is incorrect"),
    MISSING_AUTHORIZATION_HEADER(HttpStatus.UNAUTHORIZED, "This authorization header is invalid"),
    INVALID_JWT(HttpStatus.UNAUTHORIZED, "The JWT is invalid"),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "This account has been withdrawn"),

    // Auth
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "Authentication failed"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Unauthenticated"),

    // Version
    VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Version not found"),
    DUPLICATE_VERSION(HttpStatus.CONFLICT, "Duplicate version for platform"),

    // Notice
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Notice not found"),

    // Post
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "Post not found"),

    // Plan
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "Plan not found"),

    // Party
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "Party not found"),
    PARTY_RECRUITMENT_CLOSED(HttpStatus.CONFLICT, "Party recruitment is closed"),
    PARTY_FULL(HttpStatus.CONFLICT, "Party is at full capacity"),
    PARTY_JOIN_NOT_ALLOWED(HttpStatus.CONFLICT, "Cannot join this party"),
    PARTY_SELF_BAN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Cannot ban yourself"),
    PARTY_HOST_BAN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Cannot ban the host"),
    PARTY_INACTIVE_MEMBER_BAN(HttpStatus.CONFLICT, "Target member is not active"),
    PARTY_HOST_LEAVE_NOT_ALLOWED(HttpStatus.CONFLICT, "Host cannot leave the party"),
    PARTY_INACTIVE_MEMBER_LEAVE(HttpStatus.CONFLICT, "Member is not active"),
    PARTY_JOIN_ALREADY_MEMBER(HttpStatus.CONFLICT, "User is already a member"),
    PARTY_JOIN_BANNED(HttpStatus.CONFLICT, "Banned users cannot rejoin"),
    PARTY_JOIN_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "No pending join request found"),
    PARTY_JOIN_REJECTED_COOLDOWN(HttpStatus.CONFLICT, "Cannot rejoin within 24 hours of rejection"),
    PARTY_JOIN_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "Only pending join requests can be cancelled"),

    // Chat
    CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "Chat room not found"),
    CHATROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Chat room access denied"),
    EMPTY_CHAT_MESSAGE(HttpStatus.BAD_REQUEST, "Chat message is empty"),
    CHAT_MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "Chat message is too long"),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Chat message not found"),

    // Push token
    PUSH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Push token not found"),

    // Preference
    PREFERENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Preference not found"),

    // Terms
    INVALID_TERMS_UUID(HttpStatus.BAD_REQUEST, "Invalid terms UUID"),
    TERMS_NOT_FOUND(HttpStatus.NOT_FOUND, "Terms not found"),
    INACTIVE_TERMS_ACCESS(HttpStatus.BAD_REQUEST, "Inactive terms cannot be accessed"),
    INVALID_TERMS_AGREEMENT(HttpStatus.BAD_REQUEST, "Invalid terms agreement"),
    MISSING_REQUIRED_TERMS_AGREEMENT(HttpStatus.BAD_REQUEST, "Missing required terms agreement"),
    TERMS_CONTENT_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "Terms content object could not be verified"),
    DUPLICATE_TERMS_VERSION(HttpStatus.CONFLICT, "Duplicate terms version for type"),

    // External service (provider outage, not the client's fault)
    SOCIAL_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Social login provider is unavailable"),
    IMAGE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is unavailable"),

    // Idempotency
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key reused with a different request body"),

    // Concurrency
    CONCURRENT_REQUEST_CONFLICT(HttpStatus.CONFLICT, "Conflicting concurrent request, please retry"),

    // Server
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),

    // Rate limit
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
}
