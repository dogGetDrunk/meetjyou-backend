package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse

interface TermsContentUrlGenerator {
    fun generateDownloadPar(contentObjectKey: String): ParResponse
}
