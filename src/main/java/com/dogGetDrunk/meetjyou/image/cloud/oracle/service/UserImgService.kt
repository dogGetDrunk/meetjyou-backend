package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserImgService(
    private val oracleObjectStorageService: OracleObjectStorageService
) {

    fun createUserProfileImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            oracleObjectStorageService.createUploadPars(uuid, ImageTarget.USER_PROFILE_ORIGINAL),
            oracleObjectStorageService.createUploadPars(uuid, ImageTarget.USER_PROFILE_THUMBNAIL)
        )

    fun createUserProfileOriginalImgDownloadPars(uuid: UUID): ParResponse =
        oracleObjectStorageService.createDownloadPars(uuid, ImageTarget.USER_PROFILE_ORIGINAL)

    fun createUserProfileThumbnailImgDownloadPars(uuid: List<UUID>): List<ParResponse> =
        uuid.map { oracleObjectStorageService.createDownloadPars(it, ImageTarget.USER_PROFILE_THUMBNAIL) }

    fun deleteUserProfileImg(uuid: UUID) {
        oracleObjectStorageService.deleteObject(uuid, ImageTarget.USER_PROFILE_ORIGINAL)
        oracleObjectStorageService.deleteObject(uuid, ImageTarget.USER_PROFILE_THUMBNAIL)
    }
}

