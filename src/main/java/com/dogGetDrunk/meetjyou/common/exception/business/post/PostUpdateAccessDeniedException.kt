package com.dogGetDrunk.meetjyou.common.exception.business.post

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException
import java.util.UUID

class PostUpdateAccessDeniedException(
    val postUuid: UUID,
    val authorUuid: UUID,
    val userUuid: UUID,
    message: String? = null,
) : AccessDeniedException(ErrorCode.ACCESS_DENIED, postUuid.toString(), message)
