package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

/**
 * An outside dependency (social login provider, object storage) failed or was unreachable. Maps to
 * 503 so clients can tell a temporary outage from a bad request, and still raises a server alert.
 */
class ExternalServiceException(
    errorCode: ErrorCode,
    value: String,
    cause: Throwable? = null,
) : BusinessException(errorCode, value, cause = cause)
