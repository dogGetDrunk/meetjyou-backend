package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException
import com.dogGetDrunk.meetjyou.user.AuthProvider

class SocialAccountAlreadyLinkedException(
    provider: AuthProvider,
    message: String? = null,
) : DuplicateException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED, provider.name, message)
