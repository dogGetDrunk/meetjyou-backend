package com.dogGetDrunk.meetjyou.common.exception.business

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode

class VersionNotFoundException(
    val version: String
) : NotFoundException(version, ErrorCode.VERSION_NOT_FOUND)
