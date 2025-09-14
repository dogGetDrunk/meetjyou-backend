package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

open class BusinessException(
    open val value: String?,
    val errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message ?: "Business Exception: ${errorCode.message} - $value", cause) {
    override fun fillInStackTrace(): Throwable = this
}
