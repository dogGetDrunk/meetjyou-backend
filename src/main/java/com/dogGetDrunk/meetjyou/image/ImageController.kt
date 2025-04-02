package com.dogGetDrunk.meetjyou.image

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "이미지 API", description = "프로필, 모집글 이미지 업로드, 다운로드, 삭제 API")
class ImageController(
    private val imageService: ImageService,
) {
    @PostMapping("/profile/upload")
    fun uploadUserProfileImage(@RequestParam userId: String, @RequestParam file: MultipartFile) {
        val fileType = file.originalFilename?.substringAfterLast('.') ?: "jpg"
        imageService.uploadUserProfileImage(userId, file.bytes, fileType)
    }

    @GetMapping("/profile/download")
    fun downloadUserProfileImage(
        @RequestParam userId: String,
        @RequestParam(required = false, defaultValue = "false") isThumbnail: Boolean,
    ): ResponseEntity<ByteArray> {
        val image = imageService.downloadUserProfileImage(userId, isThumbnail)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image)
    }

    @DeleteMapping("/profile")
    fun deleteUserProfileImage(@RequestParam userId: String): ResponseEntity<Void> {
        return if (imageService.deleteUserProfileImage(userId)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/post/upload")
    fun uploadPostImage(@RequestParam postId: Long, @RequestParam file: MultipartFile) {
        val fileType = file.originalFilename?.substringAfterLast('.') ?: "jpg"
        imageService.uploadPostImage(postId, file.bytes, fileType)
    }

    @GetMapping("/post/download")
    fun downloadPostImage(@RequestParam postId: Long): ResponseEntity<ByteArray> {
        val image = imageService.downloadPostImage(postId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image)
    }

    @DeleteMapping("/post")
    fun deletePostImage(@RequestParam postId: Long): ResponseEntity<Void> {
        return if (imageService.deletePostImage(postId)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
