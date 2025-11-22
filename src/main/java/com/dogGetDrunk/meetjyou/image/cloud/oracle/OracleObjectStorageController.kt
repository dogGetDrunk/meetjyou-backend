package com.dogGetDrunk.meetjyou.image.cloud.oracle

import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.post.PostUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.BulkParRequest
import com.dogGetDrunk.meetjyou.image.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgParService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgParService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.UserImageParService
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.post.PostService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/")
@Tag(name = "이미지 API", description = "프로필, 모집글, 파티 이미지 관련 API를 제공합니다.")
class OracleObjectStorageController(
    private val userImgParService: UserImageParService,
    private val postImgParService: PostImgParService,
    private val partyImgParService: PartyImgParService,
    private val postService: PostService,
    private val partyService: PartyService
) {

    @Operation(
        summary = "유저 프로필 이미지 업로드 PAR URL 생성",
        description = "유저 프로필 이미지(원본 및 썸네일) 업로드를 위한 PAR URL을 생성합니다."
    )
    @PostMapping("/users/me/img/profile/par/upload")
    fun createUserImgUploadPar(): ResponseEntity<List<ParResponse>> {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        val response = userImgParService.createUserProfileImgUploadPars(userUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "유저 프로필 원본 이미지 다운로드 PAR URL 생성")
    @PostMapping("/users/{userUuid}/img/profile/original/par/download")
    fun createUserOriginalImgDownloadPar(@PathVariable userUuid: UUID): ResponseEntity<ParResponse> {
        val response = userImgParService.createUserProfileOriginalImgDownloadPars(userUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "유저 프로필 썸네일 이미지 다운로드 PAR URL 생성",
        description = "1명 이상의 프로필 썸네일 이미지 다운로드를 위한 PAR URL을 생성합니다."
    )
    @PostMapping("/users/img/profile/thumbnail/par/download")
    fun createUserThumbnailImgDownloadPar(@RequestBody request: BulkParRequest): ResponseEntity<List<ParResponse>> {
        val response = userImgParService.createUserProfileThumbnailImgDownloadPars(request.uuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "모집글 이미지 업로드 PAR URL 생성",
        description = "모집글 이미지(원본 및 썸네일) 업로드를 위한 PAR URL을 생성합니다."
    )
    @PostMapping("/posts/{postUuid}/img/par/upload")
    fun createPostImgUploadPar(@PathVariable postUuid: UUID): ResponseEntity<List<ParResponse>> {
        val userUuid = SecurityUtil.getCurrentUserUuid()

        if (!postService.verifyPostAuthor(postUuid, userUuid)) {
            throw PostUpdateAccessDeniedException(postUuid, userUuid)
        }

        val response = postImgParService.createPostImgUploadPars(postUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "모집글 원본 이미지 다운로드 PAR URL 생성")
    @PostMapping("/posts/{postUuid}/img/original/par/download")
    fun createPostOriginalImgDownloadPar(@PathVariable postUuid: UUID): ResponseEntity<ParResponse> {
        val response = postImgParService.createPostOriginalImgDownloadPars(postUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "모집글 썸네일 이미지 다운로드 PAR URL 생성",
        description = "1개 이상의 모집글 썸네일 이미지 다운로드를 위한 PAR URL을 생성합니다."
    )
    @PostMapping("/posts/img/thumbnail/par/download")
    fun createPostThumbnailImgDownloadPar(@RequestBody request: BulkParRequest): ResponseEntity<List<ParResponse>> {
        val response = postImgParService.createPostThumbnailImgDownloadPars(request.uuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "파티 이미지 업로드 PAR URL 생성",
        description = "파티 이미지(원본 및 썸네일) 업로드를 위한 PAR URL을 생성합니다."
    )
    @PostMapping("/parties/{partyUuid}/img/par/upload")
    fun createPartyImgUploadPar(@PathVariable partyUuid: UUID): ResponseEntity<List<ParResponse>> {
        val userUuid = SecurityUtil.getCurrentUserUuid()

        if (!partyService.verifyPartyOwner(partyUuid, userUuid)) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }

        val response = partyImgParService.createPartyImgUploadPars(partyUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "파티 원본 이미지 다운로드 PAR URL 생성")
    @PostMapping("/parties/{partyUuid}/img/original/par/download")
    fun createPartyOriginalImgDownloadPar(@PathVariable partyUuid: UUID): ResponseEntity<ParResponse> {
        val response = partyImgParService.createPartyOriginalImgDownloadPars(partyUuid)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "파티 썸네일 이미지 다운로드 PAR URL 생성",
        description = "1개 이상의 파티 썸네일 이미지 다운로드를 위한 PAR URL을 생성합니다."
    )
    @PostMapping("/parties/img/thumbnail/par/download")
    fun createPartyThumbnailImgDownloadPar(@RequestBody request: BulkParRequest): ResponseEntity<List<ParResponse>> {
        val response = partyImgParService.createPartyThumbnailImgDownloadPars(request.uuid)
        return ResponseEntity.ok(response)
    }
}

