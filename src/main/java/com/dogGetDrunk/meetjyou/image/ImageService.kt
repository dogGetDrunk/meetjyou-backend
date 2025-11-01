package com.dogGetDrunk.meetjyou.image

import org.springframework.web.multipart.MultipartFile
import java.util.UUID

interface ImageService {
    fun uploadUserProfileImage(file: MultipartFile): Boolean
    fun downloadOriginalUserProfileImage(userUuid: UUID): ByteArray?
    fun downloadThumbnailUserProfileImage(userUuid: UUID): ByteArray?
    fun deleteUserProfileImage(): Boolean

    fun uploadPostImage(postUuid: UUID, file: MultipartFile): Boolean
    fun downloadOriginalPostImage(postUuid: UUID): ByteArray?
    fun downloadThumbnailPostImage(postUuid: UUID): ByteArray?
    fun deletePostImage(postUuid: UUID): Boolean

    fun uploadPartyImage(partyUuid: UUID, file: MultipartFile): Boolean
    fun downloadOriginalPartyImage(partyUuid: UUID): ByteArray?
    fun downloadThumbnailPartyImage(partyUuid: UUID): ByteArray?
    fun deletePartyImage(partyUuid: UUID): Boolean
    fun setDefaultPartyImage(partyUuid: UUID, postUuid: UUID): Boolean
}
