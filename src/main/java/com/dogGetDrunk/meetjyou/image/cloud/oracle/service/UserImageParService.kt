package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserImageParService(
    private val objectStorageParService: ObjectStorageParService,
) {

    fun createUserProfileImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            objectStorageParService.createUploadPars(uuid, ImageTarget.USER_PROFILE_ORIGINAL),
            objectStorageParService.createUploadPars(uuid, ImageTarget.USER_PROFILE_THUMBNAIL)
        )

    fun createUserProfileOriginalImgDownloadPars(uuid: UUID): ParResponse =
        objectStorageParService.createDownloadPars(uuid, ImageTarget.USER_PROFILE_ORIGINAL)

    fun createUserProfileThumbnailImgDownloadPars(uuid: List<UUID>): List<ParResponse> =
        uuid.map { objectStorageParService.createDownloadPars(it, ImageTarget.USER_PROFILE_THUMBNAIL) }
}

