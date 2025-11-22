package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PostImgParService(
    private val objectStorageParService: ObjectStorageParService
) {

    fun createPostImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            objectStorageParService.createDownloadPars(uuid, ImageTarget.POST_ORIGINAL),
            objectStorageParService.createDownloadPars(uuid, ImageTarget.POST_THUMBNAIL)
        )

    fun createPostOriginalImgDownloadPars(uuid: UUID): ParResponse =
        objectStorageParService.createDownloadPars(uuid, ImageTarget.POST_ORIGINAL)

    fun createPostThumbnailImgDownloadPars(uuid: List<UUID>): List<ParResponse> =
        uuid.map { objectStorageParService.createDownloadPars(it, ImageTarget.POST_THUMBNAIL) }
}
