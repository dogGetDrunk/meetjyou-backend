package com.dogGetDrunk.meetjyou.common.exception.business.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

/**
 * Not a JWT failure: the credential is valid but the account is gone. Kept out of the 401 JWT
 * family so clients don't treat it as "refresh the token and retry".
 */
class UserWithdrawnException(
    value: String? = null,
    message: String? = null,
) : BusinessException(ErrorCode.USER_WITHDRAWN, value, message)
