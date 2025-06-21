package com.dogGetDrunk.meetjyou.image

import org.springframework.web.multipart.MultipartFile
import java.util.UUID

interface ImageService {
    fun uploadUserProfileImage(userId: String, file: ByteArray, fileType: String): Boolean
    fun downloadUserProfileImage(userId: String, isThumbnail: Boolean): ByteArray?
    fun deleteUserProfileImage(userId: String): Boolean

    fun uploadPostImage(uuid: UUID, file: MultipartFile): Boolean
    fun downloadPostImage(uuid: UUID): ByteArray?
    fun deletePostImage(uuid: UUID): Boolean

    fun uploadPartyImage(uuid: UUID, file: MultipartFile): Boolean
    fun downloadOriginalPartyImage(uuid: UUID): ByteArray?
    fun downloadThumbnailPartyImage(uuid: UUID): ByteArray?
    fun deletePartyImage(uuid: UUID): Boolean

    fun setDefaultPartyImage(partyUuid: UUID, postUuid: UUID): Boolean
}
