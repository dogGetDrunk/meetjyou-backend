package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.config.OracleProps
import com.dogGetDrunk.meetjyou.image.ImageOperation
import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.BulkParRequest
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParRequest
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

@Service
class ObjectStorageParService(
    private val objectStorageClient: ObjectStorageClient,
    private val props: OracleProps
) {

    private val log = LoggerFactory.getLogger(ObjectStorageParService::class.java)

    fun createUploadPars(uuid: UUID, target: ImageTarget): ParResponse {
        val request = ParRequest(
            uuid = uuid,
            target = target,
            operation = ImageOperation.UPLOAD
        )
        return createPars(request)
    }

    fun createDownloadPars(uuid: UUID, target: ImageTarget): ParResponse {
        val request = ParRequest(
            uuid = uuid,
            target = target,
            operation = ImageOperation.DOWNLOAD
        )
        return createPars(request)
    }

    fun createPars(requests: List<ParRequest>): List<ParResponse> =
        requests.map { createPars(it) }

    fun createPars(request: BulkParRequest): List<ParResponse> =
        request.uuid.map { uuid ->
            val parRequest = ParRequest(
                uuid = uuid,
                target = request.target,
                operation = request.operation
            )
            createPars(parRequest)
        }

    fun createPars(request: ParRequest): ParResponse {
        val now = Instant.now()
        val expiresAtInstant = now.plus(props.parExpirationMinutes, ChronoUnit.MINUTES)
        val expiresAtSeoul = expiresAtInstant.atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime()
        val accessType = when (request.operation) {
            ImageOperation.UPLOAD -> CreatePreauthenticatedRequestDetails.AccessType.ObjectWrite
            ImageOperation.DOWNLOAD -> CreatePreauthenticatedRequestDetails.AccessType.ObjectRead
        }

        log.info(
            "Creating OCI PARs. uuid={}, target={}, operation={}",
            request.uuid,
            request.target,
            request.operation
        )

        val objectName = request.target.toObjectName(request.uuid)

        val details = CreatePreauthenticatedRequestDetails.builder()
            .name(buildParName(request, now))
            .objectName(objectName)
            .accessType(accessType)
            .timeExpires(Date.from(expiresAtInstant))
            .build()

        val parRequest = CreatePreauthenticatedRequestRequest.builder()
            .namespaceName(props.namespace)
            .bucketName(props.bucketName)
            .createPreauthenticatedRequestDetails(details)
            .build()

        val response = objectStorageClient.createPreauthenticatedRequest(parRequest)
        val par = response.preauthenticatedRequest
        check(par != null) { "Failed to create OCI PAR" }

        val accessUri = par.accessUri
        val parUrl = props.parBaseUrl + accessUri

        val httpMethod = when (request.operation) {
            ImageOperation.UPLOAD -> "PUT"
            ImageOperation.DOWNLOAD -> "GET"
        }

        log.info(
            "Created OCI PAR. objectName={}, accessType={}, httpMethod={}, expiresAt={}, parUrl={}",
            objectName,
            accessType,
            httpMethod,
            expiresAtSeoul,
            parUrl
        )

        return ParResponse(
            parUrl = parUrl,
            httpMethod = httpMethod,
            expiresAt = expiresAtSeoul
        )

    }

    private fun buildParName(request: ParRequest, now: Instant) =
        buildString {
            append("meetjyou-")
            append("${request.operation.name.lowercase()}-")
            append("${request.target.name.lowercase()}-")
            append("${request.uuid}-")
            append(now.toEpochMilli())
        }

}
