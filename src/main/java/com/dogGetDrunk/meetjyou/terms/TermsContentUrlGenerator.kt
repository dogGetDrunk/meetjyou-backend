package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.terms.dto.TermsUploadPar

interface TermsContentUrlGenerator {
    fun generateDownloadPar(contentObjectKey: String): ParResponse

    fun generateUploadPar(type: TermsType, version: String): TermsUploadPar

    fun verifyContent(objectKey: String, expectedHash: String): Boolean
}
