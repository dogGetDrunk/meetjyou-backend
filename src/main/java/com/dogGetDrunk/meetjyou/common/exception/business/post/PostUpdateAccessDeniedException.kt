package com.dogGetDrunk.meetjyou.common.exception.business.post

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException
import java.util.UUID

class PostUpdateAccessDeniedException(
    postUuid: UUID,
    authorUuid: UUID?,
    userUuid: UUID,
    message: String? = null,
) : AccessDeniedException(ErrorCode.ACCESS_DENIED, postUuid.toString(), message)
