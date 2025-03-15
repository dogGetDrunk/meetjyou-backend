package com.dogGetDrunk.meetjyou.image

interface ImageService {
    /**
     * 사용자의 프로필 이미지를 업로드합니다.
     * 기존 이미지가 존재하면 삭제 후 새로운 이미지를 저장합니다.
     * 썸네일도 자동으로 생성하여 함께 저장됩니다.
     *
     * @param userId 사용자 ID
     * @param file 이미지 데이터 (ByteArray)
     * @param fileType 이미지 확장자 (예: "jpg", "png")
     * @return 저장된 원본 이미지의 URL
     */
    fun uploadImage(userId: String, file: ByteArray, fileType: String): String

    /**
     * 사용자의 프로필 이미지를 다운로드합니다.
     * @param userId 사용자 ID
     * @param isThumbnail 썸네일 여부 (true = 썸네일, false = 원본)
     * @return 이미지 데이터 (ByteArray) 또는 존재하지 않을 경우 null
     */
    fun downloadImage(userId: String, isThumbnail: Boolean): ByteArray?

    /**
     * 사용자의 프로필 이미지를 삭제합니다. (원본과 썸네일 둘 다 삭제)
     * @param userId 사용자 ID
     * @return 삭제 성공 여부
     */
    fun deleteImage(userId: String): Boolean
}
