package com.dogGetDrunk.meetjyou.cloud.oracle

import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.config.OracleProps
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import com.oracle.bmc.objectstorage.requests.GetObjectRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

@Service
class OracleObjectStorageService(
    private val objectStorageClient: ObjectStorageClient,
    private val props: OracleProps,
) {
    private val log = LoggerFactory.getLogger(OracleObjectStorageService::class.java)

    private val downloadParCache: Cache<String, ParResponse> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(maxOf(props.parExpirationMinutes * 4 / 5, 1L)))
        .maximumSize(1024)
        .build()

    fun createUploadPar(objectKey: String): ParResponse {
        return createPar(
            objectKey = objectKey,
            accessType = CreatePreauthenticatedRequestDetails.AccessType.ObjectWrite,
            httpMethod = "PUT",
            operation = "upload",
        )
    }

    fun createDownloadPar(objectKey: String): ParResponse {
        downloadParCache.getIfPresent(objectKey)?.let { cached ->
            log.debug("Download PAR cache hit. objectKey={}", objectKey)
            return cached
        }

        val par = createPar(
            objectKey = objectKey,
            accessType = CreatePreauthenticatedRequestDetails.AccessType.ObjectRead,
            httpMethod = "GET",
            operation = "download",
        )
        downloadParCache.put(objectKey, par)
        return par
    }

    fun getObjectContent(objectKey: String): ByteArray? {
        val request = GetObjectRequest.builder()
            .namespaceName(props.namespace)
            .bucketName(props.bucketName)
            .objectName(objectKey)
            .build()

        return try {
            objectStorageClient.getObject(request).inputStream.use { it.readBytes() }
        } catch (exception: BmcException) {
            if (exception.statusCode == 404) {
                log.warn("Object not found in OCI Object Storage. objectKey={}", objectKey)
                return null
            }
            throw exception
        }
    }

    fun deleteObject(objectKey: String): Boolean {
        downloadParCache.invalidate(objectKey)
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

        log.info("Creating OCI PAR. objectKey={}, accessType={}, httpMethod={}", objectKey, accessType, httpMethod)

        val par = requestPar(objectKey, accessType, operation, now, expiresAt)
        val parUrl = "${props.parBaseUrl}${par.accessUri}"

        // The PAR URL itself grants anonymous read/write until expiresAt, so it is deliberately
        // never logged — only enough context to correlate this creation in the logs.
        log.info(
            "Created OCI PAR. objectKey={}, accessType={}, httpMethod={}, expiresAt={}",
            objectKey,
            accessType,
            httpMethod,
            expiresAt,
        )

        return ParResponse(
            url = parUrl,
            httpMethod = httpMethod,
            expiresAt = expiresAt,
        )
    }

    private fun requestPar(
        objectKey: String,
        accessType: CreatePreauthenticatedRequestDetails.AccessType,
        operation: String,
        now: Instant,
        expiresAt: Instant,
    ): PreauthenticatedRequest {
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
        return response.preauthenticatedRequest ?: error("Failed to create OCI PAR.")
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
