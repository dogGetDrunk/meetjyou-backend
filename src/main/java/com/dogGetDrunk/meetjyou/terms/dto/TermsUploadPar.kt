package com.dogGetDrunk.meetjyou.terms.dto

import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse

data class TermsUploadPar(
    val objectKey: String,
    val par: ParResponse,
)
