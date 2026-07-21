package com.dogGetDrunk.meetjyou.common.exception.business.terms

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateException

class DuplicateTermsVersionException(value: String) : DuplicateException(ErrorCode.DUPLICATE_TERMS_VERSION, value)
