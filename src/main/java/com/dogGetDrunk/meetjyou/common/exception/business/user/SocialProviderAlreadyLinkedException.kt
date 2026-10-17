package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException
import com.dogGetDrunk.meetjyou.user.AuthProvider

class SocialProviderAlreadyLinkedException(
    provider: AuthProvider,
) : DuplicateException(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED, provider.name)
