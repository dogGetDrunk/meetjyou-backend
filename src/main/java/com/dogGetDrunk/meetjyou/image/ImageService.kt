package com.dogGetDrunk.meetjyou.image

import org.springframework.web.multipart.MultipartFile
import java.util.UUID

interface ImageService {
    fun uploadUserProfileImage(file: MultipartFile): Boolean
    fun downloadOriginalUserProfileImage(userUuid: UUID): ByteArray?
    fun downloadThumbnailUserProfileImage(userUuid: UUID): ByteArray?
    fun deleteUserProfileImage(): Boolean

    fun uploadPostImage(uuid: UUID, file: MultipartFile): Boolean
    fun downloadPostImage(uuid: UUID): ByteArray?
    fun deletePostImage(uuid: UUID): Boolean

    fun uploadPartyImage(uuid: UUID, file: MultipartFile): Boolean
    fun downloadOriginalPartyImage(uuid: UUID): ByteArray?
    fun downloadThumbnailPartyImage(uuid: UUID): ByteArray?
    fun deletePartyImage(uuid: UUID): Boolean

    fun setDefaultPartyImage(partyUuid: UUID, postUuid: UUID): Boolean
}
