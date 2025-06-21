package com.dogGetDrunk.meetjyou.common.exception.business.image

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

class ImageUploadFailedException(
    value: String,
    errorCode: ErrorCode = ErrorCode.IMAGE_UPLOAD_FAILED
) : BusinessException(value, errorCode)
