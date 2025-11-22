package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PartyImgParService(
    private val objectStorageParService: ObjectStorageParService
) {

    fun createPartyImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            objectStorageParService.createUploadPars(uuid, ImageTarget.PARTY_ORIGINAL),
            objectStorageParService.createUploadPars(uuid, ImageTarget.PARTY_THUMBNAIL)
        )

    fun createPartyOriginalImgDownloadPars(uuid: UUID): ParResponse =
        objectStorageParService.createDownloadPars(uuid, ImageTarget.PARTY_ORIGINAL)

    fun createPartyThumbnailImgDownloadPars(uuid: List<UUID>): List<ParResponse> =
        uuid.map { objectStorageParService.createDownloadPars(it, ImageTarget.PARTY_THUMBNAIL) }
}
