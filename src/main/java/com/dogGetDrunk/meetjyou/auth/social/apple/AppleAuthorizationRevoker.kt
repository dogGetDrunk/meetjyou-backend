package com.dogGetDrunk.meetjyou.auth.social.apple

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Component

/**
 * Revokes a user's Sign in with Apple authorization when they withdraw or unlink Apple
 * (App Store Review Guideline 5.1.1(v), #171).
 *
 * The client re-authenticates with Apple right before the request and sends the fresh
 * authorization code; nothing is stored, so accounts created before this existed are covered too.
 *
 * Best-effort by design: never throws. Deleting the account must not depend on Apple being up or
 * on the client sending a valid code — every miss is logged, counted, and failures alerted.
 */
@Component
class AppleAuthorizationRevoker(
    private val props: AppleOidcProperties,
    private val appleAuthClient: AppleAuthClient,
    @Qualifier("appleJwtDecoder") private val appleJwtDecoder: JwtDecoder,
    private val meterRegistry: MeterRegistry,
    private val discordAlertService: DiscordAlertService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * @param appleSubject the user's linked Apple `sub`; null when they have no Apple account,
     *   in which case nothing is revoked and any code is ignored.
     */
    fun revokeQuietly(authorizationCode: String?, appleSubject: String?, trigger: Trigger) {
        if (appleSubject == null) return
        val outcome = when {
            authorizationCode.isNullOrBlank() -> Outcome.MISSING_CODE
            !props.revocationEnabled -> Outcome.DISABLED
            else -> exchangeAndRevoke(authorizationCode, appleSubject, trigger)
        }
        record(outcome, trigger)
    }

    // Catch-all on purpose: this runs after the withdrawal/unlink committed, so any escaping
    // exception (even an unforeseen one) would turn a completed request into a 500 for the client.
    @Suppress("TooGenericExceptionCaught")
    private fun exchangeAndRevoke(authorizationCode: String, appleSubject: String, trigger: Trigger): Outcome =
        try {
            val tokens = appleAuthClient.exchangeCode(authorizationCode)
            val tokenSubject = appleJwtDecoder.decode(tokens.idToken).subject
            if (tokenSubject != appleSubject) {
                // The code belongs to a different Apple ID (e.g. another one signed in on the device).
                Outcome.SUBJECT_MISMATCH
            } else {
                revoke(tokens)
                Outcome.REVOKED
            }
        } catch (e: Exception) {
            log.error("Apple authorization revoke failed. trigger={}, ex={}", trigger, e::class.java.simpleName, e)
            discordAlertService.sendOperationalAlert(
                source = "$ALERT_SOURCE_PREFIX ${trigger.name}",
                exceptionClass = e::class.java.simpleName,
                summary = "Apple 연동 해제 실패. 탈퇴·연결 해제는 완료됐고 애플 측 연동만 남아 있음",
                detail = e.message,
            )
            Outcome.FAILED
        }

    // Revoking the refresh token also invalidates its access tokens; Apple may omit it, then the access token.
    private fun revoke(tokens: AppleTokenResponse) {
        val refreshToken = tokens.refreshToken
        if (refreshToken != null) {
            appleAuthClient.revoke(refreshToken, AppleAuthClient.TOKEN_TYPE_HINT_REFRESH)
        } else {
            appleAuthClient.revoke(tokens.accessToken, AppleAuthClient.TOKEN_TYPE_HINT_ACCESS)
        }
    }

    private fun record(outcome: Outcome, trigger: Trigger) {
        meterRegistry.counter(METRIC_NAME, "outcome", outcome.tag, "trigger", trigger.tag).increment()
        when (outcome) {
            Outcome.REVOKED -> log.info("Apple authorization revoked. trigger={}", trigger)
            Outcome.FAILED -> Unit // logged with the cause where it happened
            else -> log.warn("Apple authorization not revoked. trigger={}, outcome={}", trigger, outcome)
        }
    }

    enum class Trigger(val tag: String) { WITHDRAWAL("withdrawal"), UNLINK("unlink") }

    enum class Outcome(val tag: String) {
        REVOKED("revoked"),
        MISSING_CODE("missing_code"),
        DISABLED("disabled"),
        SUBJECT_MISMATCH("subject_mismatch"),
        FAILED("failed"),
    }

    companion object {
        const val METRIC_NAME = "auth.apple.revoke"
        private const val ALERT_SOURCE_PREFIX = "Apple revoke"
    }
}
