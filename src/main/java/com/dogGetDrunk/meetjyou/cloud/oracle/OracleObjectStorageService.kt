package com.dogGetDrunk.meetjyou.cloud.oracle

import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.config.OracleProps
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

@Service
class OracleObjectStorageService(
    private val objectStorageClient: ObjectStorageClient,
    private val props: OracleProps,
) {
    private val log = LoggerFactory.getLogger(OracleObjectStorageService::class.java)

    fun createUploadPar(objectKey: String): ParResponse {
        return createPar(
            objectKey = objectKey,
            accessType = CreatePreauthenticatedRequestDetails.AccessType.ObjectWrite,
            httpMethod = "PUT",
            operation = "upload",
        )
    }

    fun createDownloadPar(objectKey: String): ParResponse {
        return createPar(
            objectKey = objectKey,
            accessType = CreatePreauthenticatedRequestDetails.AccessType.ObjectRead,
            httpMethod = "GET",
            operation = "download",
        )
    }

    fun deleteObject(objectKey: String): Boolean {
        return try {
            val deleteRequest = DeleteObjectRequest.builder()
                .namespaceName(props.namespace)
                .bucketName(props.bucketName)
                .objectName(objectKey)
                .build()

            objectStorageClient.deleteObject(deleteRequest)

            log.info("Deleted object from OCI Object Storage. objectKey={}", objectKey)
            true
        } catch (exception: Exception) {
            log.error(
                "Failed to delete object from OCI Object Storage. objectKey={}",
                objectKey,
                exception,
            )
            false
        }
    }

    private fun createPar(
        objectKey: String,
        accessType: CreatePreauthenticatedRequestDetails.AccessType,
        httpMethod: String,
        operation: String,
    ): ParResponse {
        val now = Instant.now()
        val expiresAt = now.plus(props.parExpirationMinutes, ChronoUnit.MINUTES)

        log.info(
            "Creating OCI PAR. objectKey={}, accessType={}, httpMethod={}",
            objectKey,
            accessType,
            httpMethod,
        )

        val details = CreatePreauthenticatedRequestDetails.builder()
            .name(buildParName(objectKey, operation, now))
            .objectName(objectKey)
            .accessType(accessType)
            .timeExpires(Date.from(expiresAt))
            .build()

        val request = CreatePreauthenticatedRequestRequest.builder()
            .namespaceName(props.namespace)
            .bucketName(props.bucketName)
            .createPreauthenticatedRequestDetails(details)
            .build()

        val response = objectStorageClient.createPreauthenticatedRequest(request)
        val par = response.preauthenticatedRequest
        check(par != null) { "Failed to create OCI PAR." }

        val accessUri = par.accessUri
        val parUrl = props.parBaseUrl + accessUri

        log.info(
            "Created OCI PAR. objectKey={}, accessType={}, httpMethod={}, expiresAt={}, parUrl={}",
            objectKey,
            accessType,
            httpMethod,
            expiresAt,
            parUrl,
        )

        return ParResponse(
            url = parUrl,
            httpMethod = httpMethod,
            expiresAt = expiresAt,
        )
    }

    private fun buildParName(
        objectKey: String,
        operation: String,
        now: Instant,
    ): String {
        val sanitizedObjectKey = objectKey
            .replace("/", "-")
            .replace("_", "-")

        return "meetjyou-$operation-$sanitizedObjectKey-${now.toEpochMilli()}"
    }
}
