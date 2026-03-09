package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.cloud.oracle.OracleObjectStorageService
import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.image.ImageTarget
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PartyImgService(
    private val oracleObjectStorageService: OracleObjectStorageService,
) {
    fun createPartyImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            oracleObjectStorageService.createUploadPar(ImageTarget.PARTY_ORIGINAL.toObjectName(uuid)),
            oracleObjectStorageService.createUploadPar(ImageTarget.PARTY_THUMBNAIL.toObjectName(uuid)),
        )

    fun createPartyOriginalImgDownloadPars(uuid: UUID): ParResponse =
        oracleObjectStorageService.createDownloadPar(ImageTarget.PARTY_ORIGINAL.toObjectName(uuid))

    fun createPartyThumbnailImgDownloadPars(uuids: List<UUID>): List<ParResponse> =
        uuids.map {
            oracleObjectStorageService.createDownloadPar(ImageTarget.PARTY_THUMBNAIL.toObjectName(it))
        }

    fun deletePartyImg(uuid: UUID) {
        oracleObjectStorageService.deleteObject(ImageTarget.PARTY_ORIGINAL.toObjectName(uuid))
        oracleObjectStorageService.deleteObject(ImageTarget.PARTY_THUMBNAIL.toObjectName(uuid))
    }
}
