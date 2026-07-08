package com.dogGetDrunk.meetjyou.notification.event

import com.dogGetDrunk.meetjyou.terms.TermsType
import java.util.UUID

data class TermsReconsentEvent(
    val termsUuid: UUID,
    val termsType: TermsType,
    val displayText: String,
)
