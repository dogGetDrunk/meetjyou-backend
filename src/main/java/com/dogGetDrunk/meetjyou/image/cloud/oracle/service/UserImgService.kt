package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.cloud.oracle.OracleObjectStorageService
import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.image.ImageTarget
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserImgService(
    private val oracleObjectStorageService: OracleObjectStorageService,
) {
    fun createUserProfileImgUploadPars(uuid: UUID): List<ParResponse> =
        listOf(
            oracleObjectStorageService.createUploadPar(ImageTarget.USER_PROFILE_ORIGINAL.toObjectName(uuid)),
            oracleObjectStorageService.createUploadPar(ImageTarget.USER_PROFILE_THUMBNAIL.toObjectName(uuid)),
        )

    fun createUserProfileOriginalImgDownloadPars(uuid: UUID): ParResponse =
        oracleObjectStorageService.createDownloadPar(ImageTarget.USER_PROFILE_ORIGINAL.toObjectName(uuid))

    fun createUserProfileThumbnailImgDownloadPars(uuids: List<UUID>): List<ParResponse> =
        uuids.map {
            oracleObjectStorageService.createDownloadPar(ImageTarget.USER_PROFILE_THUMBNAIL.toObjectName(it))
        }

    fun deleteUserProfileImg(uuid: UUID) {
        oracleObjectStorageService.deleteObject(ImageTarget.USER_PROFILE_ORIGINAL.toObjectName(uuid))
        oracleObjectStorageService.deleteObject(ImageTarget.USER_PROFILE_THUMBNAIL.toObjectName(uuid))
    }
}
