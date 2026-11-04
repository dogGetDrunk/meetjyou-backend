package com.dogGetDrunk.meetjyou.common.exception.business.terms

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException
import java.util.UUID

class TermsReconsentNotApplicableException(
    termsUuid: UUID,
) : BusinessException(ErrorCode.TERMS_RECONSENT_NOT_APPLICABLE, termsUuid.toString())
