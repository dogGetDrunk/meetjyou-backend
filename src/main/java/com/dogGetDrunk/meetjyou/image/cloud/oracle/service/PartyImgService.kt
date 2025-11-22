package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PartyImgService(
    private val objectStorageService: ObjectStorageService
) {

    fun createPartyImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            objectStorageService.createUploadPars(uuid, ImageTarget.PARTY_ORIGINAL),
            objectStorageService.createUploadPars(uuid, ImageTarget.PARTY_THUMBNAIL)
        )

    fun createPartyOriginalImgDownloadPars(uuid: UUID): ParResponse =
        objectStorageService.createDownloadPars(uuid, ImageTarget.PARTY_ORIGINAL)

    fun createPartyThumbnailImgDownloadPars(uuid: List<UUID>): List<ParResponse> =
        uuid.map { objectStorageService.createDownloadPars(it, ImageTarget.PARTY_THUMBNAIL) }

    fun deletePartyImg(uuid: UUID) {
        objectStorageService.deleteObject(uuid, ImageTarget.PARTY_ORIGINAL)
        objectStorageService.deleteObject(uuid, ImageTarget.PARTY_THUMBNAIL)
    }
}
