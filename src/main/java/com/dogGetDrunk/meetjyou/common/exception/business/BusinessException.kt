package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

open class BusinessException(
    override val message: String,
    val errorCode: ErrorCode
) : RuntimeException(message) {
    override fun fillInStackTrace(): Throwable = this
}
