package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.cloud.oracle.OracleObjectStorageService
import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.image.ImageTarget
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PostImgService(
    private val oracleObjectStorageService: OracleObjectStorageService,
) {
    fun createPostImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            oracleObjectStorageService.createUploadPar(ImageTarget.POST_ORIGINAL.toObjectName(uuid)),
            oracleObjectStorageService.createUploadPar(ImageTarget.POST_THUMBNAIL.toObjectName(uuid)),
        )

    fun createPostOriginalImgDownloadPars(uuid: UUID): ParResponse =
        oracleObjectStorageService.createDownloadPar(ImageTarget.POST_ORIGINAL.toObjectName(uuid))

    fun createPostThumbnailImgDownloadPars(uuids: List<UUID>): List<ParResponse> =
        uuids.map {
            oracleObjectStorageService.createDownloadPar(ImageTarget.POST_THUMBNAIL.toObjectName(it))
        }

    fun deletePostImg(uuid: UUID) {
        oracleObjectStorageService.deleteObject(ImageTarget.POST_ORIGINAL.toObjectName(uuid))
        oracleObjectStorageService.deleteObject(ImageTarget.POST_THUMBNAIL.toObjectName(uuid))
    }
}
