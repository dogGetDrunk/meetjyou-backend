package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PostImgService(
    private val objectStorageService: ObjectStorageService
) {

    fun createPostImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            objectStorageService.createDownloadPars(uuid, ImageTarget.POST_ORIGINAL),
            objectStorageService.createDownloadPars(uuid, ImageTarget.POST_THUMBNAIL)
        )

    fun createPostOriginalImgDownloadPars(uuid: UUID): ParResponse =
        objectStorageService.createDownloadPars(uuid, ImageTarget.POST_ORIGINAL)

    fun createPostThumbnailImgDownloadPars(uuid: List<UUID>): List<ParResponse> =
        uuid.map { objectStorageService.createDownloadPars(it, ImageTarget.POST_THUMBNAIL) }

    fun deletePostImg(uuid: UUID) {
        objectStorageService.deleteObject(uuid, ImageTarget.POST_ORIGINAL)
        objectStorageService.deleteObject(uuid, ImageTarget.POST_THUMBNAIL)
    }
}
