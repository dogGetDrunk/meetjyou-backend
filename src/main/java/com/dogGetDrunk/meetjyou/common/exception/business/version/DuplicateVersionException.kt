package com.dogGetDrunk.meetjyou.common.exception.business.version

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException

class DuplicateVersionException(value: String) : DuplicateException(ErrorCode.DUPLICATE_VERSION, value)
