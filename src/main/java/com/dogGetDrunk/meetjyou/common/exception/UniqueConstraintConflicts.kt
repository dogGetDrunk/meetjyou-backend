package com.dogGetDrunk.meetjyou.common.exception

import org.springframework.dao.DataIntegrityViolationException

/**
 * Unique indexes a concurrent request can lose on a path with no translation of its own: the
 * existence check passed for both requests, the index decided. Names must match the Flyway
 * migrations. An unlisted constraint (or FK / NOT NULL violation) is a bug and stays a 500.
 *
 * Paths that settle their race themselves (sign-up, nickname, social link, idempotency keys)
 * catch the violation before it reaches here.
 */
object UniqueConstraintConflicts {

    private val ERROR_CODE_BY_CONSTRAINT = mapOf(
        // Retrying re-reads the winner's row and succeeds (join stays PENDING, the update re-applies).
        "uk_user_party" to ErrorCode.CONCURRENT_REQUEST_CONFLICT,
        "uq_user_preference" to ErrorCode.CONCURRENT_REQUEST_CONFLICT,
        "uk_unp_user_type" to ErrorCode.CONCURRENT_REQUEST_CONFLICT,
        // Admin writes: the existence check already returns these codes, this covers the race.
        "uk_terms_type_version" to ErrorCode.DUPLICATE_TERMS_VERSION,
        "uk_version_platform" to ErrorCode.DUPLICATE_VERSION,
    )

    fun errorCodeFor(e: DataIntegrityViolationException): ErrorCode? =
        e.violatedConstraintName()?.let(ERROR_CODE_BY_CONSTRAINT::get)
}
