package com.dogGetDrunk.meetjyou.common.exception.business.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.AccessDeniedException
import java.util.UUID

class PartyMemberAccessDeniedException(
    partyUuid: UUID,
    userUuid: UUID,
    message: String? = "User $userUuid is not a joined member of Party $partyUuid",
) : AccessDeniedException(ErrorCode.ACCESS_DENIED, partyUuid.toString(), message)
