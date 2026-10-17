package com.dogGetDrunk.meetjyou.common.exception.business.notFound

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.user.AuthProvider

class SocialAccountNotLinkedException(
    provider: AuthProvider,
) : NotFoundException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED, provider.name)
