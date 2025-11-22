//package com.dogGetDrunk.meetjyou.image
//
//import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
//import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyUpdateAccessDeniedException
//import com.dogGetDrunk.meetjyou.common.exception.business.post.PostUpdateAccessDeniedException
//import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
//import com.dogGetDrunk.meetjyou.party.PartyService
//import com.dogGetDrunk.meetjyou.post.PostService
//import io.swagger.v3.oas.annotations.Operation
//import io.swagger.v3.oas.annotations.media.Content
//import io.swagger.v3.oas.annotations.media.Schema
//import io.swagger.v3.oas.annotations.responses.ApiResponse
//import io.swagger.v3.oas.annotations.responses.ApiResponses
//import io.swagger.v3.oas.annotations.tags.Tag
//import org.springframework.http.MediaType
//import org.springframework.http.ResponseEntity
//import org.springframework.web.bind.annotation.DeleteMapping
//import org.springframework.web.bind.annotation.GetMapping
//import org.springframework.web.bind.annotation.PathVariable
//import org.springframework.web.bind.annotation.PostMapping
//import org.springframework.web.bind.annotation.RequestMapping
//import org.springframework.web.bind.annotation.RequestParam
//import org.springframework.web.bind.annotation.RestController
//import org.springframework.web.multipart.MultipartFile
//import java.util.UUID
//
//@RestController
//@RequestMapping("/api/v1")
//@Tag(name = "이미지 API", description = "프로필, 모집글 이미지 업로드, 다운로드, 삭제 API")
//class ImageController(
//    private val imageService: ImageService,
//    private val postService: PostService,
//    private val partyService: PartyService,
//) {
//
//    @Operation(summary = "유저 프로필 이미지 업로드")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "200", description = "업로드 성공"),
//            ApiResponse(
//                responseCode = "400",
//                description = "업로드 실패",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @PostMapping("/users/me/profile/img")
//    fun uploadUserProfileImage(@RequestParam file: MultipartFile): ResponseEntity<Unit> {
//        return if (imageService.uploadUserProfileImage(file)) {
//            ResponseEntity.ok().build()
//        } else {
//            ResponseEntity.badRequest().build()
//        }
//    }
//
//    @Operation(summary = "유저 프로필 원본 이미지 다운로드")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "200", description = "다운로드 성공", content = [Content(mediaType = "image/jpeg")]),
//            ApiResponse(
//                responseCode = "404",
//                description = "이미지 없음",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @GetMapping("/users/{userUuid}/profile/img/original")
//    fun downloadOriginalUserProfileImage(@PathVariable userUuid: UUID): ResponseEntity<ByteArray> {
//        val image = imageService.downloadOriginalUserProfileImage(userUuid)
//            ?: return ResponseEntity.notFound().build()
//        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image)
//    }
//
//    @Operation(summary = "유저 프로필 썸네일 이미지 다운로드")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "200", description = "다운로드 성공", content = [Content(mediaType = "image/jpeg")]),
//            ApiResponse(
//                responseCode = "404",
//                description = "이미지 없음",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @GetMapping("/users/{userUuid}/profile/img/thumbnail")
//    fun downloadThumbnailUserProfileImage(@PathVariable userUuid: UUID): ResponseEntity<ByteArray> {
//        val image = imageService.downloadThumbnailUserProfileImage(userUuid)
//            ?: return ResponseEntity.notFound().build()
//        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image)
//    }
//
//    @Operation(summary = "유저 프로필 이미지 삭제")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "204", description = "삭제 성공"),
//            ApiResponse(
//                responseCode = "404",
//                description = "이미지 없음",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @DeleteMapping("/users/me/profile/img")
//    fun deleteUserProfileImage(): ResponseEntity<Unit> {
//        return if (imageService.deleteUserProfileImage()) {
//            ResponseEntity.noContent().build()
//        } else {
//            ResponseEntity.notFound().build()
//        }
//    }
//
//    @Operation(summary = "모집글 이미지 업로드")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "200", description = "업로드 성공"),
//            ApiResponse(
//                responseCode = "400",
//                description = "업로드 실패",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @PostMapping("/posts/{postUuid}/img")
//    fun uploadPostImage(@PathVariable postUuid: UUID, @RequestParam file: MultipartFile): ResponseEntity<Unit> {
//        val userUuid = SecurityUtil.getCurrentUserUuid()
//
//        if (!postService.verifyPostAuthor(postUuid, userUuid)) {
//            throw PostUpdateAccessDeniedException(postUuid, null, userUuid)
//        }
//
//        return if (imageService.uploadPostImage(postUuid, file)) {
//            ResponseEntity.ok().build()
//        } else {
//            ResponseEntity.badRequest().build()
//        }
//    }
//
//    @Operation(summary = "모집글 원본 이미지 다운로드")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "200", description = "다운로드 성공", content = [Content(mediaType = "image/jpeg")]),
//            ApiResponse(
//                responseCode = "404",
//                description = "이미지 없음",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @GetMapping("/posts/{postUuid}/img/original")
//    fun downloadOriginalPostImage(@PathVariable postUuid: UUID): ResponseEntity<ByteArray> {
//        val image = imageService.downloadOriginalPostImage(postUuid)
//            ?: return ResponseEntity.notFound().build()
//        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image)
//    }
//
//    @Operation(summary = "모집글 썸네일 이미지 다운로드")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "200", description = "다운로드 성공", content = [Content(mediaType = "image/jpeg")]),
//            ApiResponse(
//                responseCode = "404",
//                description = "이미지 없음",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @GetMapping("/posts/{postUuid}/img/thumbnail")
//    fun downloadThumbnailPostImage(@PathVariable postUuid: UUID): ResponseEntity<ByteArray> {
//        val image = imageService.downloadThumbnailPostImage(postUuid)
//            ?: return ResponseEntity.notFound().build()
//        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image)
//    }
//
//    @Operation(summary = "모집글 이미지 삭제")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "204", description = "삭제 성공"),
//            ApiResponse(
//                responseCode = "404",
//                description = "이미지 없음",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @DeleteMapping("/post/{postUuid}/img")
//    fun deletePostImage(@PathVariable postUuid: UUID): ResponseEntity<Unit> {
//        val userUuid = SecurityUtil.getCurrentUserUuid()
//
//        if (!postService.verifyPostAuthor(postUuid, userUuid)) {
//            throw PostUpdateAccessDeniedException(
//                postUuid,
//                null,
//                userUuid,
//                "Only the author can delete images for this post."
//            )
//        }
//        return if (imageService.deletePostImage(postUuid)) {
//            ResponseEntity.noContent().build()
//        } else {
//            ResponseEntity.notFound().build()
//        }
//    }
//
//    @Operation(summary = "파티 이미지 업로드")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "200", description = "업로드 성공"),
//            ApiResponse(
//                responseCode = "400",
//                description = "업로드 실패",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @PostMapping("/parties/{partyUuid}/img")
//    fun uploadPartyImage(@PathVariable partyUuid: UUID, @RequestParam file: MultipartFile): ResponseEntity<Unit> {
//        val userUuid = SecurityUtil.getCurrentUserUuid()
//
//        if (partyService.verifyPartyOwner(partyUuid, userUuid)) {
//            throw PartyUpdateAccessDeniedException(partyUuid, "Only the party owner can upload images for this party.")
//        }
//
//        return if (imageService.uploadPartyImage(partyUuid, file)) {
//            ResponseEntity.ok().build()
//        } else {
//            ResponseEntity.badRequest().build()
//        }
//    }
//
//    @Operation(summary = "파티 이미지 원본 다운로드")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "200", description = "다운로드 성공", content = [Content(mediaType = "image/jpeg")]),
//            ApiResponse(
//                responseCode = "404",
//                description = "이미지 없음",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @GetMapping("/parties/{partyUuid}/img/original")
//    fun downloadOriginalPartyImage(@PathVariable partyUuid: UUID): ResponseEntity<ByteArray> {
//        val image = imageService.downloadOriginalPartyImage(partyUuid)
//            ?: return ResponseEntity.notFound().build()
//        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image)
//    }
//
//    @Operation(summary = "파티 이미지 썸네일 다운로드")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "200", description = "다운로드 성공", content = [Content(mediaType = "image/jpeg")]),
//            ApiResponse(
//                responseCode = "404",
//                description = "이미지 없음",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @GetMapping("/parties/{partyUuid}/img/thumbnail")
//    fun downloadThumbnailPartyImage(@PathVariable partyUuid: UUID): ResponseEntity<ByteArray> {
//        val image = imageService.downloadThumbnailPartyImage(partyUuid)
//            ?: return ResponseEntity.notFound().build()
//        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image)
//    }
//
//    @Operation(summary = "파티 이미지 삭제")
//    @ApiResponses(
//        value = [
//            ApiResponse(responseCode = "204", description = "삭제 성공"),
//            ApiResponse(
//                responseCode = "404",
//                description = "이미지 없음",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @DeleteMapping("/parties/{partyUuid}/img")
//    fun deletePartyImage(@PathVariable partyUuid: UUID): ResponseEntity<Unit> {
//        val userUuid = SecurityUtil.getCurrentUserUuid()
//
//        if (partyService.verifyPartyOwner(partyUuid, userUuid)) {
//            throw PartyUpdateAccessDeniedException(partyUuid, "Only the party owner can delete images for this party.")
//        }
//
//        return if (imageService.deletePartyImage(partyUuid)) {
//            ResponseEntity.noContent().build()
//        } else {
//            ResponseEntity.notFound().build()
//        }
//    }
//}
