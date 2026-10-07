package com.dogGetDrunk.meetjyou.auth.social

/**
 * Log-only reasons for rejecting a provider token. They go into the exception message, which is
 * logged but never put in the response body, so clients still can't probe which check failed.
 */
object SocialTokenRejection {
    const val NONCE_MISMATCH = "Nonce missing or mismatched"
    const val MISSING_SUBJECT = "Missing sub claim"
    const val MISSING_EMAIL = "Missing email claim"
    const val EMAIL_NOT_VERIFIED = "Email not verified by provider"
}
