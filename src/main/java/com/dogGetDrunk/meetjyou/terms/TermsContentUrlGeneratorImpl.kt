package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.cloud.oracle.OracleObjectStorageService
import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TermsContentUrlGeneratorImpl(
    private val oracleObjectStorageService: OracleObjectStorageService,
) : TermsContentUrlGenerator {
    private val log = LoggerFactory.getLogger(TermsContentUrlGeneratorImpl::class.java)

    override fun generateDownloadPar(contentObjectKey: String): ParResponse {
        log.info("Generating terms download PAR. contentObjectKey={}", contentObjectKey)
        return oracleObjectStorageService.createDownloadPar(contentObjectKey)
    }
}
