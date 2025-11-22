package com.dogGetDrunk.meetjyou.common.exception.business.post

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException
import java.util.UUID

class PostUpdateAccessDeniedException(
    postUuid: UUID,
    userUuid: UUID,
    authorUuid: UUID? = null,
    message: String? = null,
) : AccessDeniedException(ErrorCode.ACCESS_DENIED, postUuid.toString(), message)
