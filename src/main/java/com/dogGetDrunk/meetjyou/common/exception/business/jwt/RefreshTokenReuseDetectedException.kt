package com.dogGetDrunk.meetjyou.common.exception.business.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

/**
 * A revoked refresh token was presented outside the rotation grace window. Same client-facing
 * error as [InvalidJwtException]; a separate type so the refresh transaction can commit the
 * session revocation it made right before throwing instead of rolling it back.
 */
class RefreshTokenReuseDetectedException(
    message: String? = null,
) : CustomJwtException(ErrorCode.INVALID_JWT, null, message)
