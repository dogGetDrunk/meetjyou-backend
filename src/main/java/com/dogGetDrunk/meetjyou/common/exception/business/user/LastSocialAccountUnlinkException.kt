package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.user.AuthProvider

class LastSocialAccountUnlinkException(
    provider: AuthProvider,
) : InvalidInputException(ErrorCode.LAST_SOCIAL_ACCOUNT_UNLINK, provider.name)
