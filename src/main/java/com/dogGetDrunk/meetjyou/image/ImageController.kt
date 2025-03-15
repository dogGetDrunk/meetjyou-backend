package com.dogGetDrunk.meetjyou.image

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
class ImageController(private val imageService: ImageService) {

    @PostMapping("/upload")
    fun uploadImage(@RequestParam userId: String, @RequestParam file: MultipartFile): ResponseEntity<String> {
        val fileType = file.originalFilename?.substringAfterLast('.') ?: "jpg"
        val fileUrl = imageService.uploadImage(userId, file.bytes, fileType)
        return ResponseEntity.ok(fileUrl)
    }

    @GetMapping("/download")
    fun downloadImage(
        @RequestParam userId: String,
        @RequestParam(required = false, defaultValue = "false") isThumbnail: Boolean
    ): ResponseEntity<ByteArray> {
        val image = imageService.downloadImage(userId, isThumbnail) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image)
    }

    @DeleteMapping("/delete")
    fun deleteImage(@RequestParam userId: String): ResponseEntity<Void> {
        return if (imageService.deleteImage(userId)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
