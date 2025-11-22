package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserImgService(
    private val objectStorageService: ObjectStorageService
) {

    fun createUserProfileImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            objectStorageService.createUploadPars(uuid, ImageTarget.USER_PROFILE_ORIGINAL),
            objectStorageService.createUploadPars(uuid, ImageTarget.USER_PROFILE_THUMBNAIL)
        )

    fun createUserProfileOriginalImgDownloadPars(uuid: UUID): ParResponse =
        objectStorageService.createDownloadPars(uuid, ImageTarget.USER_PROFILE_ORIGINAL)

    fun createUserProfileThumbnailImgDownloadPars(uuid: List<UUID>): List<ParResponse> =
        uuid.map { objectStorageService.createDownloadPars(it, ImageTarget.USER_PROFILE_THUMBNAIL) }

    fun deleteUserProfileImg(uuid: UUID) {
        objectStorageService.deleteObject(uuid, ImageTarget.USER_PROFILE_ORIGINAL)
        objectStorageService.deleteObject(uuid, ImageTarget.USER_PROFILE_THUMBNAIL)
    }
}

