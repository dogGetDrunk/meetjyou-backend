package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.cloud.oracle.OracleObjectStorageService
import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.terms.dto.TermsUploadPar
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class TermsContentUrlGeneratorImpl(
    private val oracleObjectStorageService: OracleObjectStorageService,
) : TermsContentUrlGenerator {
    private val log = LoggerFactory.getLogger(TermsContentUrlGeneratorImpl::class.java)

    override fun generateDownloadPar(contentObjectKey: String): ParResponse {
        log.info("Generating terms download PAR. contentObjectKey={}", contentObjectKey)
        return oracleObjectStorageService.createDownloadPar(contentObjectKey)
    }

    override fun generateUploadPar(type: TermsType, version: String): TermsUploadPar {
        val objectKey = type.toObjectKey(version)
        log.info("Generating terms upload PAR. type={}, objectKey={}", type, objectKey)

        val par = oracleObjectStorageService.createUploadPar(objectKey)
        return TermsUploadPar(objectKey = objectKey, par = par)
    }

    override fun verifyContent(objectKey: String, expectedHash: String): Boolean {
        val content = oracleObjectStorageService.getObjectContent(objectKey) ?: return false
        val actualHash = sha256Hex(content)

        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    private fun sha256Hex(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
