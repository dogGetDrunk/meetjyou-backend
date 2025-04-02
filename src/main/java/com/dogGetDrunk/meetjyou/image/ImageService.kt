package com.dogGetDrunk.meetjyou.image

interface ImageService {
    fun uploadUserProfileImage(userId: String, file: ByteArray, fileType: String): Boolean
    fun downloadUserProfileImage(userId: String, isThumbnail: Boolean): ByteArray?
    fun deleteUserProfileImage(userId: String): Boolean

    fun uploadPostImage(postId: Long, file: ByteArray, fileType: String): Boolean
    fun downloadPostImage(postId: Long): ByteArray?
    fun deletePostImage(postId: Long): Boolean
}
