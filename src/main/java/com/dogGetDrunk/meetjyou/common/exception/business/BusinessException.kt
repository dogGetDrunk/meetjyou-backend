package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

open class BusinessException(
    open val value: String?,
    val errorCode: ErrorCode
) : RuntimeException("Business Exception: ${errorCode.message} - $value") {
    override fun fillInStackTrace(): Throwable = this
}
