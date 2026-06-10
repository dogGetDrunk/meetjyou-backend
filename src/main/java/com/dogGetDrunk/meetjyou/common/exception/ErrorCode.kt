package com.dogGetDrunk.meetjyou.common.exception

enum class ErrorCode(
    val message: String,
) {
    // Business
    DUPLICATE("Duplicate value"),
    NOT_FOUND("Value not found"),
    INVALID_INPUT_VALUE("Invalid input value"),

    // User
    DUPLICATE_USER("Duplicate email"),
    DUPLICATE_NICKNAME("Duplicate nickname"),
    EMAIL_NOT_FOUND("Email not found"),
    USER_NOT_FOUND("User not found"),
    INVALID_NICKNAME("Invalid nickname"),
    INVALID_EMAIL_FORMAT("Invalid email format"),
    TOO_LONG_BIO("Bio is too long"),
    TOO_MANY_PERSONALITIES("Too many personalities"),
    TOO_MANY_TRAVEL_STYLES("Too many travel styles"),

    // JWT
    TOKEN_COMMON("The token has an issue"),
    EXPIRED_TOKEN("The token has expired"),
    INCORRECT_TOKEN_SUBJECT("The subject in the token is incorrect"),
    MISSING_AUTHORIZATION_HEADER("This authorization header is invalid"),
    INVALID_ACCESS_TOKEN("The access token is invalid"),
    INVALID_JWT("The JWT is invalid"),

    // Auth
    AUTHENTICATION_FAILED("Authentication failed"),
    ACCESS_DENIED("Access denied"),
    UNAUTHENTICATED("Unauthenticated"),

    // Version
    VERSION_NOT_FOUND("Version not found"),
    DUPLICATE_VERSION("Duplicate version for platform"),

    // Notice
    NOTICE_NOT_FOUND("Notice not found"),

    // Post
    POST_NOT_FOUND("Post not found"),

    // Plan
    PLAN_NOT_FOUND("Plan not found"),
    MARKER_NOT_FOUND("Marker not found"),

    // Party
    PARTY_NOT_FOUND("Party not found"),
    PARTY_RECRUITMENT_CLOSED("Party recruitment is closed"),
    PARTY_FULL("Party is at full capacity"),
    PARTY_JOIN_NOT_ALLOWED("Cannot join this party"),
    PARTY_SELF_BAN_NOT_ALLOWED("Cannot ban yourself"),
    PARTY_HOST_BAN_NOT_ALLOWED("Cannot ban the host"),
    PARTY_INACTIVE_MEMBER_BAN("Target member is not active"),
    PARTY_HOST_LEAVE_NOT_ALLOWED("Host cannot leave the party"),
    PARTY_INACTIVE_MEMBER_LEAVE("Member is not active"),
    PARTY_JOIN_ALREADY_PENDING("Join request is already pending"),
    PARTY_JOIN_ALREADY_MEMBER("User is already a member"),
    PARTY_JOIN_BANNED("Banned users cannot rejoin"),
    PARTY_JOIN_REQUEST_NOT_FOUND("No pending join request found"),
    PARTY_JOIN_REJECTED_COOLDOWN("Cannot rejoin within 24 hours of rejection"),
    PARTY_JOIN_CANCEL_NOT_ALLOWED("Only pending join requests can be cancelled"),

    // Image
    IMAGE_UPLOAD_FAILED("Image upload failed"),

    // Chat
    CHATROOM_NOT_FOUND("Chat room not found"),
    CHATROOM_ACCESS_DENIED("Chat room access denied"),
    EMPTY_CHAT_MESSAGE("Chat message is empty"),
    CHAT_MESSAGE_TOO_LONG("Chat message is too long"),
    CHAT_MESSAGE_NOT_FOUND("Chat message not found"),

    // Push token
    PUSH_TOKEN_NOT_FOUND("Push token not found"),

    // Preference
    PREFERENCE_NOT_FOUND("Preference not found"),

    // Terms
    INVALID_TERMS_UUID("Invalid terms UUID"),
    TERMS_NOT_FOUND("Terms not found"),
    INACTIVE_TERMS_ACCESS("Inactive terms cannot be accessed"),
    INVALID_TERMS_AGREEMENT("Invalid terms agreement"),
    MISSING_REQUIRED_TERMS_AGREEMENT("Missing required terms agreement"),

    // Server
    INTERNAL_SERVER_ERROR("Internal server error"),
}
