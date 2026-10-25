package com.dogGetDrunk.meetjyou.common.exception

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus

/**
 * Pins the status every ErrorCode responds with. A status change must edit this table too, so it
 * shows up in review as a deliberate client-facing change instead of a silent side effect.
 */
class ErrorCodeStatusTest : BehaviorSpec({

    given("모든 ErrorCode") {
        `when`("응답 상태를 확인하면") {
            then("표에 없는 코드가 없다") {
                ErrorCode.entries.toList() shouldContainExactlyInAnyOrder EXPECTED_STATUS.keys
            }

            then("각 코드가 표의 상태로 응답한다") {
                ErrorCode.entries.forEach { code ->
                    (code to code.status) shouldBe (code to EXPECTED_STATUS.getValue(code))
                }
            }
        }
    }
})

private val EXPECTED_STATUS: Map<ErrorCode, HttpStatus> = mapOf(
    // 400
    ErrorCode.CHAT_MESSAGE_TOO_LONG to HttpStatus.BAD_REQUEST,
    ErrorCode.EMPTY_CHAT_MESSAGE to HttpStatus.BAD_REQUEST,
    ErrorCode.INACTIVE_TERMS_ACCESS to HttpStatus.BAD_REQUEST,
    ErrorCode.INVALID_INPUT_VALUE to HttpStatus.BAD_REQUEST,
    ErrorCode.INVALID_TERMS_AGREEMENT to HttpStatus.BAD_REQUEST,
    ErrorCode.INVALID_TERMS_UUID to HttpStatus.BAD_REQUEST,
    ErrorCode.MISSING_REQUIRED_TERMS_AGREEMENT to HttpStatus.BAD_REQUEST,
    ErrorCode.PARTY_HOST_BAN_NOT_ALLOWED to HttpStatus.BAD_REQUEST,
    ErrorCode.PARTY_SELF_BAN_NOT_ALLOWED to HttpStatus.BAD_REQUEST,
    ErrorCode.TERMS_CONTENT_VERIFICATION_FAILED to HttpStatus.BAD_REQUEST,
    // 401
    ErrorCode.AUTHENTICATION_FAILED to HttpStatus.UNAUTHORIZED,
    ErrorCode.EXPIRED_TOKEN to HttpStatus.UNAUTHORIZED,
    ErrorCode.INCORRECT_TOKEN_SUBJECT to HttpStatus.UNAUTHORIZED,
    ErrorCode.INVALID_JWT to HttpStatus.UNAUTHORIZED,
    ErrorCode.MISSING_AUTHORIZATION_HEADER to HttpStatus.UNAUTHORIZED,
    ErrorCode.TOKEN_COMMON to HttpStatus.UNAUTHORIZED,
    ErrorCode.UNAUTHENTICATED to HttpStatus.UNAUTHORIZED,
    // 403
    ErrorCode.ACCESS_DENIED to HttpStatus.FORBIDDEN,
    ErrorCode.CHATROOM_ACCESS_DENIED to HttpStatus.FORBIDDEN,
    ErrorCode.USER_WITHDRAWN to HttpStatus.FORBIDDEN,
    // 404
    ErrorCode.CHATROOM_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.CHAT_MESSAGE_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.NOTICE_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.PARTY_JOIN_REQUEST_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.PARTY_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.PLAN_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.POST_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.PREFERENCE_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.PUSH_TOKEN_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED to HttpStatus.NOT_FOUND,
    ErrorCode.TERMS_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.USER_NOT_FOUND to HttpStatus.NOT_FOUND,
    ErrorCode.VERSION_NOT_FOUND to HttpStatus.NOT_FOUND,
    // 405 · 413 · 415
    ErrorCode.METHOD_NOT_ALLOWED to HttpStatus.METHOD_NOT_ALLOWED,
    ErrorCode.PAYLOAD_TOO_LARGE to HttpStatus.PAYLOAD_TOO_LARGE,
    ErrorCode.UNSUPPORTED_MEDIA_TYPE to HttpStatus.UNSUPPORTED_MEDIA_TYPE,
    // 409
    ErrorCode.CONCURRENT_REQUEST_CONFLICT to HttpStatus.CONFLICT,
    ErrorCode.DUPLICATE to HttpStatus.CONFLICT,
    ErrorCode.DUPLICATE_NICKNAME to HttpStatus.CONFLICT,
    ErrorCode.DUPLICATE_TERMS_VERSION to HttpStatus.CONFLICT,
    ErrorCode.DUPLICATE_USER to HttpStatus.CONFLICT,
    ErrorCode.DUPLICATE_VERSION to HttpStatus.CONFLICT,
    ErrorCode.IDEMPOTENCY_KEY_REUSED to HttpStatus.CONFLICT,
    ErrorCode.LAST_SOCIAL_ACCOUNT_UNLINK to HttpStatus.CONFLICT,
    ErrorCode.PARTY_FULL to HttpStatus.CONFLICT,
    ErrorCode.PARTY_HOST_LEAVE_NOT_ALLOWED to HttpStatus.CONFLICT,
    ErrorCode.PARTY_INACTIVE_MEMBER_BAN to HttpStatus.CONFLICT,
    ErrorCode.PARTY_INACTIVE_MEMBER_LEAVE to HttpStatus.CONFLICT,
    ErrorCode.PARTY_JOIN_ALREADY_MEMBER to HttpStatus.CONFLICT,
    ErrorCode.PARTY_JOIN_BANNED to HttpStatus.CONFLICT,
    ErrorCode.PARTY_JOIN_CANCEL_NOT_ALLOWED to HttpStatus.CONFLICT,
    ErrorCode.PARTY_JOIN_NOT_ALLOWED to HttpStatus.CONFLICT,
    ErrorCode.PARTY_JOIN_REJECTED_COOLDOWN to HttpStatus.CONFLICT,
    ErrorCode.PARTY_RECRUITMENT_CLOSED to HttpStatus.CONFLICT,
    ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED to HttpStatus.CONFLICT,
    ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED to HttpStatus.CONFLICT,
    // 429
    ErrorCode.RATE_LIMIT_EXCEEDED to HttpStatus.TOO_MANY_REQUESTS,
    // 500
    ErrorCode.INTERNAL_SERVER_ERROR to HttpStatus.INTERNAL_SERVER_ERROR,
    // 503
    ErrorCode.IMAGE_STORAGE_UNAVAILABLE to HttpStatus.SERVICE_UNAVAILABLE,
    ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE to HttpStatus.SERVICE_UNAVAILABLE,
)
