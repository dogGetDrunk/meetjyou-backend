package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.post.PostService
import com.dogGetDrunk.meetjyou.post.dto.GetPostResponse
import com.dogGetDrunk.meetjyou.user.dto.AdvancedUserResponse
import com.dogGetDrunk.meetjyou.user.dto.BasicUserResponse
import com.dogGetDrunk.meetjyou.user.dto.UserUpdateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "유저 정보 API")
class UserController(
    private val userService: UserService,
    private val postService: PostService,
) {
    @Operation(
        summary = "유저의 기본 정보 조회",
        description = "유저의 기본 정보(닉네임, 한 줄 소개, 프사 썸네일, 성별, 나이) 및 라이프스타일(성격, 여행 스타일, 식단, 기타)을 조회합니다."
    )
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = BasicUserResponse::class)
                )
            )
        ), ApiResponse(
            responseCode = "404",
            description = "유저를 찾을 수 없음",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )
            )
        )]
    )
    @GetMapping("/{uuid}/basic-info")
    fun getBasicUserProfile(@PathVariable uuid: UUID): ResponseEntity<BasicUserResponse> {
        val response = userService.getUserProfile(uuid)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "유저의 모든 정보 조회", description = "유저의 기본 정보, 라이프스타일 및 작성한 모집글을 조회합니다.")
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdvancedUserResponse::class)
                )
            )
        ), ApiResponse(
            responseCode = "404",
            description = "유저를 찾을 수 없음",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )
            )
        )]
    )
    @GetMapping("/{uuid}/advanced-info")
    fun getAdvancedUserProfile(
        @PathVariable uuid: UUID,
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ResponseEntity<AdvancedUserResponse> {
        val basicUserResponseDto: BasicUserResponse = userService.getUserProfile(uuid)
        val posts: Page<GetPostResponse> = postService.getPostByAuthorUuid(uuid, pageable)
        return ResponseEntity.ok(AdvancedUserResponse(basicUserResponseDto, posts))
    }

    @Operation(summary = "유저 정보 수정", description = "유저의 닉네임, 한 줄 소개, 성별, 나이 및 라이프스타일을 수정합니다.")
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "수정 성공",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = BasicUserResponse::class)
                )
            )
        ), ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 데이터",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )
            )
        ), ApiResponse(
            responseCode = "404",
            description = "유저를 찾을 수 없음",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )
            )
        )]
    )
    @PutMapping
    fun updateUser(
        @Valid @RequestBody request: UserUpdateRequest
    ): ResponseEntity<BasicUserResponse> {
        val updatedUser: BasicUserResponse = userService.updateUser(request)
        return ResponseEntity.ok(updatedUser)
    }

    @GetMapping
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(
                        type = "array",
                        implementation = BasicUserResponse::class
                    )
                )
            )
        )]
    )
    @Operation(summary = "[admin] 모든 유저 프로필 조회")
    fun allUsersProfile(): ResponseEntity<List<BasicUserResponse>> {
        return ResponseEntity.ok(userService.getAllUsersProfile())
    }

    @Operation(summary = "유저 닉네임 중복 확인")
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "complete",
            content = arrayOf(Content(schema = Schema(example = "{ \"isDuplicate\": true }")))
        )]
    )
    @GetMapping("/is-duplicate-nickname")
    fun checkNicknameDuplication(@RequestParam nickname: String): ResponseEntity<Map<String, Boolean>> {
        val response: MutableMap<String, Boolean> = HashMap()
        response["isDuplicate"] = userService.isDuplicateNickname(nickname)

        return ResponseEntity.ok(response)
    }

    @Operation(summary = "회원 탈퇴", description = "현재는 DELETE, 추후 PATCH로 변경될 수 있음.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "회원 탈퇴 성공"
            ), ApiResponse(
                responseCode = "401",
                description = "유효하지 않은 액세스 토큰입니다.",
                content = arrayOf(
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = com.dogGetDrunk.meetjyou.common.exception.ErrorResponse::class)
                    )
                )
            )]
    )
    @DeleteMapping("/{uuid}")
    fun withdraw(@RequestHeader("Authorization") authorizationHeader: String, @PathVariable uuid: UUID) {
        val accessToken = authorizationHeader.substring("Bearer ".length)
        userService.withdrawUser(uuid, accessToken)
    }
}
