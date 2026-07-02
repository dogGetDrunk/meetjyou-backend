package com.dogGetDrunk.meetjyou.cloud.oracle

import com.dogGetDrunk.meetjyou.cloud.oracle.dto.BulkRequest
import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.post.PostUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.UserImgService
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.post.PostService
import com.dogGetDrunk.meetjyou.user.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID

@RestControllerV1
@RequestMapping
@Tag(name = "이미지 API", description = "프로필, 모집글, 파티 이미지 관련 API를 제공합니다.")
class OracleObjectStorageController(
    private val userImgService: UserImgService,
    private val postImgService: PostImgService,
    private val partyImgService: PartyImgService,
    private val postService: PostService,
    private val partyService: PartyService,
    private val userService: UserService,
) {
    @Operation(
        summary = "유저 프로필 이미지 업로드 PAR URL 생성",
        description = "유저 프로필 이미지(원본 및 썸네일) 업로드를 위한 PAR URL을 생성합니다.",
    )
    @PostMapping("/users/me/img/profile/par/upload")
    fun createUserImgUploadPar(): ResponseEntity<List<ParResponse>> {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        val response = userImgService.createUserProfileImgUploadPars(userUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "유저 프로필 원본 이미지 다운로드 PAR URL 생성")
    @PostMapping("/users/{userUuid}/img/profile/original/par/download")
    fun createUserOriginalImgDownloadPar(
        @PathVariable userUuid: UUID,
    ): ResponseEntity<ParResponse> {
        val response = userImgService.createUserProfileOriginalImgDownloadPars(userUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "유저 프로필 썸네일 이미지 다운로드 PAR URL 생성",
        description = "1명 이상의 프로필 썸네일 이미지 다운로드를 위한 PAR URL을 생성합니다.",
    )
    @PostMapping("/users/img/profile/thumbnail/par/download")
    fun createUserThumbnailImgDownloadPar(
        @RequestBody request: BulkRequest,
    ): ResponseEntity<List<ParResponse>> {
        val response = userImgService.createUserProfileThumbnailImgDownloadPars(request.uuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "유저 프로필 이미지 삭제",
        description = "유저 프로필 이미지(원본 및 썸네일)를 삭제합니다.",
    )
    @DeleteMapping("/users/me/img/profile")
    fun deleteUserProfileImg(): ResponseEntity<Unit> {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        userImgService.deleteUserProfileImg(userUuid)
        userService.clearProfileImage()
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "모집글 이미지 업로드 PAR URL 생성",
        description = "모집글 이미지(원본 및 썸네일) 업로드를 위한 PAR URL을 생성합니다.",
    )
    @PostMapping("/posts/{postUuid}/img/par/upload")
    fun createPostImgUploadPar(
        @PathVariable postUuid: UUID,
    ): ResponseEntity<List<ParResponse>> {
        val userUuid = SecurityUtil.getCurrentUserUuid()

        if (!postService.verifyPostAuthor(postUuid, userUuid)) {
            throw PostUpdateAccessDeniedException(postUuid, userUuid)
        }

        val response = postImgService.createPostImgUploadPars(postUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "모집글 원본 이미지 다운로드 PAR URL 생성")
    @PostMapping("/posts/{postUuid}/img/original/par/download")
    fun createPostOriginalImgDownloadPar(
        @PathVariable postUuid: UUID,
    ): ResponseEntity<ParResponse> {
        val response = postImgService.createPostOriginalImgDownloadPars(postUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "모집글 썸네일 이미지 다운로드 PAR URL 생성",
        description = "1개 이상의 모집글 썸네일 이미지 다운로드를 위한 PAR URL을 생성합니다.",
    )
    @PostMapping("/posts/img/thumbnail/par/download")
    fun createPostThumbnailImgDownloadPar(
        @RequestBody request: BulkRequest,
    ): ResponseEntity<List<ParResponse>> {
        val response = postImgService.createPostThumbnailImgDownloadPars(request.uuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "모집글 이미지 삭제",
        description = "모집글 이미지(원본 및 썸네일)를 삭제합니다.",
    )
    @DeleteMapping("/posts/{postUuid}/img")
    fun deletePostImg(
        @PathVariable postUuid: UUID,
    ): ResponseEntity<Unit> {
        val userUuid = SecurityUtil.getCurrentUserUuid()

        if (!postService.verifyPostAuthor(postUuid, userUuid)) {
            throw PostUpdateAccessDeniedException(postUuid, userUuid)
        }

        postImgService.deletePostImg(postUuid)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "파티 이미지 업로드 PAR URL 생성",
        description = "파티 이미지(원본 및 썸네일) 업로드를 위한 PAR URL을 생성합니다.",
    )
    @PostMapping("/parties/{partyUuid}/img/par/upload")
    fun createPartyImgUploadPar(
        @PathVariable partyUuid: UUID,
    ): ResponseEntity<List<ParResponse>> {
        val userUuid = SecurityUtil.getCurrentUserUuid()

        if (!partyService.verifyPartyHost(partyUuid, userUuid)) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }

        val response = partyImgService.createPartyImgUploadPars(partyUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "파티 원본 이미지 다운로드 PAR URL 생성",
        description = "파티가 자체 이미지를 보유하지 않은 경우 연결된 모집글의 이미지를 대신 반환하며, 호스트가 이미지를 삭제한 경우 204를 반환합니다.",
    )
    @PostMapping("/parties/{partyUuid}/img/original/par/download")
    fun createPartyOriginalImgDownloadPar(
        @PathVariable partyUuid: UUID,
    ): ResponseEntity<ParResponse> {
        val response = partyService.resolvePartyOriginalImageDownload(partyUuid)
            ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "파티 썸네일 이미지 다운로드 PAR URL 생성",
        description = "1개 이상의 파티 썸네일 이미지 다운로드를 위한 PAR URL을 생성합니다. 이미지가 없는 파티는 null로 반환됩니다.",
    )
    @PostMapping("/parties/img/thumbnail/par/download")
    fun createPartyThumbnailImgDownloadPar(
        @RequestBody request: BulkRequest,
    ): ResponseEntity<List<ParResponse?>> {
        val response = partyService.resolvePartyThumbnailImageDownloads(request.uuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "파티 이미지 삭제",
        description = "파티 이미지(원본 및 썸네일)를 삭제합니다.",
    )
    @DeleteMapping("/parties/{partyUuid}/img")
    fun deletePartyImg(
        @PathVariable partyUuid: UUID,
    ): ResponseEntity<Unit> {
        val userUuid = SecurityUtil.getCurrentUserUuid()

        if (!partyService.verifyPartyHost(partyUuid, userUuid)) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }

        partyImgService.deletePartyImg(partyUuid)
        partyService.clearPartyImageState(partyUuid)
        return ResponseEntity.noContent().build()
    }
}
