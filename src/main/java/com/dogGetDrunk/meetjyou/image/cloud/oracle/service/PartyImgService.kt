package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PartyImgService(
    private val oracleObjectStorageService: OracleObjectStorageService
) {

    fun createPartyImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            oracleObjectStorageService.createUploadPars(uuid, ImageTarget.PARTY_ORIGINAL),
            oracleObjectStorageService.createUploadPars(uuid, ImageTarget.PARTY_THUMBNAIL)
        )

    fun createPartyOriginalImgDownloadPars(uuid: UUID): ParResponse =
        oracleObjectStorageService.createDownloadPars(uuid, ImageTarget.PARTY_ORIGINAL)

    fun createPartyThumbnailImgDownloadPars(uuid: List<UUID>): List<ParResponse> =
        uuid.map { oracleObjectStorageService.createDownloadPars(it, ImageTarget.PARTY_THUMBNAIL) }

    fun deletePartyImg(uuid: UUID) {
        oracleObjectStorageService.deleteObject(uuid, ImageTarget.PARTY_ORIGINAL)
        oracleObjectStorageService.deleteObject(uuid, ImageTarget.PARTY_THUMBNAIL)
    }
}
