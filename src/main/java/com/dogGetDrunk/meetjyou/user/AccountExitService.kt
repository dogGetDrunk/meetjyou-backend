package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.social.apple.AppleAuthorizationRevoker
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleAuthorizationRevoker.Trigger
import org.springframework.stereotype.Service

/**
 * Withdrawal and social unlink, followed by revoking the user's Apple authorization (#171).
 *
 * Not @Transactional on purpose: the DB change commits first in its own transaction, then Apple is
 * called with no connection held. That order means a rejected request (e.g. unlinking the last
 * account) never revokes anything, and an Apple failure can't roll the user's request back.
 */
@Service
class AccountExitService(
    private val userService: UserService,
    private val socialAccountService: SocialAccountService,
    private val appleAuthorizationRevoker: AppleAuthorizationRevoker,
) {

    fun withdraw(appleAuthorizationCode: String?) {
        val appleSubject = userService.withdrawUser()
        appleAuthorizationRevoker.revokeQuietly(appleAuthorizationCode, appleSubject, Trigger.WITHDRAWAL)
    }

    fun unlinkSocialAccount(provider: AuthProvider, appleAuthorizationCode: String?) {
        val subject = socialAccountService.unlink(provider)
        if (provider == AuthProvider.APPLE) {
            appleAuthorizationRevoker.revokeQuietly(appleAuthorizationCode, subject, Trigger.UNLINK)
        }
    }

    companion object {
        /** Fresh Sign in with Apple authorization code the app obtains right before the request. */
        const val APPLE_AUTHORIZATION_CODE_HEADER = "X-Apple-Authorization-Code"

        /** Swagger note shared by the withdrawal and unlink endpoints. */
        private const val DOC_CODE_PART = "애플 연동 회원은 요청 직전 재인증으로 받은 authorization code를"
        private const val DOC_RESULT_PART = "헤더로 보내면 애플 측 연동도 해제됨(없거나 실패해도 요청은 성공)."
        const val APPLE_CODE_HEADER_DOC = "$DOC_CODE_PART $APPLE_AUTHORIZATION_CODE_HEADER $DOC_RESULT_PART"
    }
}
