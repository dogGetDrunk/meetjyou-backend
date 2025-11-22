package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PostImgService(
    private val oracleObjectStorageService: OracleObjectStorageService
) {

    fun createPostImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            oracleObjectStorageService.createDownloadPars(uuid, ImageTarget.POST_ORIGINAL),
            oracleObjectStorageService.createDownloadPars(uuid, ImageTarget.POST_THUMBNAIL)
        )

    fun createPostOriginalImgDownloadPars(uuid: UUID): ParResponse =
        oracleObjectStorageService.createDownloadPars(uuid, ImageTarget.POST_ORIGINAL)

    fun createPostThumbnailImgDownloadPars(uuid: List<UUID>): List<ParResponse> =
        uuid.map { oracleObjectStorageService.createDownloadPars(it, ImageTarget.POST_THUMBNAIL) }

    fun deletePostImg(uuid: UUID) {
        oracleObjectStorageService.deleteObject(uuid, ImageTarget.POST_ORIGINAL)
        oracleObjectStorageService.deleteObject(uuid, ImageTarget.POST_THUMBNAIL)
    }
}
