package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.CreatePostResponse
import com.dogGetDrunk.meetjyou.post.dto.GetPostResponse
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostResponse
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostStatusRequest
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostStatusResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID

@RestControllerV1
@RequestMapping("/posts")
@Tag(name = "모집글 API", description = "모집글 생성, 조회, 수정, 삭제 등의 기능을 제공합니다.")
class PostController(
    private val postService: PostService,
) {

    @Operation(summary = "모집글 생성", description = "새로운 모집글을 생성합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "모집글 생성 성공",
                content = [Content(schema = Schema(implementation = CreatePostResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 형식",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "작성자 ID에 해당하는 유저를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping
    fun createPost(@Valid @RequestBody createPostRequest: CreatePostRequest): CreatePostResponse {
        return postService.createPost(createPostRequest)
    }

    @Operation(summary = "모든 모집글 조회하기", description = "등록된 모든 모집글을 페이지네이션하여 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "모든 모집글 조회 성공",
                content = [Content(schema = Schema(implementation = GetPostResponse::class))]
            )
        ]
    )
    @GetMapping
    fun getAllPosts(
        @ParameterObject
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable
    ): Page<GetPostResponse> {
        return postService.getAllPosts(pageable)
    }

    @Operation(summary = "작성자 기준 모집글 조회", description = "특정 작성자의 모집글 목록을 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "작성자 모집글 조회 성공",
                content = [Content(schema = Schema(implementation = GetPostResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "작성자 ID에 해당하는 유저를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @GetMapping("/author/{authorUuid}")
    fun getPostsByAuthorId(
        @PathVariable authorUuid: String,
        @ParameterObject
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable
    ): Page<GetPostResponse> {
        return postService.getPostByAuthorUuid(UUID.fromString(authorUuid), pageable)
    }

    @Operation(summary = "모집글 단건 조회", description = "uuid를 통해 특정 모집글을 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "모집글 조회 성공",
                content = [Content(schema = Schema(implementation = GetPostResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "해당 postId를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @GetMapping("/{postUuid}")
    fun getPostByUuid(@PathVariable postUuid: UUID): GetPostResponse {
        return postService.getPostByUuid(postUuid)
    }

    @Operation(summary = "모집글 수정", description = "기존 모집글을 수정합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "모집글 수정 성공",
                content = [Content(schema = Schema(implementation = UpdatePostResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 형식",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "postId 또는 authorId를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PutMapping("/{postUuid}")
    fun updatePost(
        @PathVariable postUuid: UUID,
        @Valid @RequestBody updatePostRequest: UpdatePostRequest
    ): UpdatePostResponse {
        return postService.updatePost(postUuid, updatePostRequest)
    }

    @Operation(summary = "모집글 모집 상태 변경", description = "모집글의 모집 상태(RECRUITING / RECRUITMENT_COMPLETED)를 변경합니다. 작성자만 변경할 수 있습니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "상태 변경 성공",
                content = [Content(schema = Schema(implementation = UpdatePostStatusResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 형식",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "403",
                description = "작성자가 아닌 사용자의 요청",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "postUuid에 해당하는 모집글을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PatchMapping("/{postUuid}/status")
    fun updatePostStatus(
        @PathVariable postUuid: UUID,
        @Valid @RequestBody request: UpdatePostStatusRequest,
    ): UpdatePostStatusResponse {
        return postService.updatePostStatus(postUuid, request)
    }

    @Operation(summary = "모집글 삭제", description = "uuid를 통해 모집글을 삭제합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "모집글 삭제 성공"),
            ApiResponse(
                responseCode = "404",
                description = "postId를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @DeleteMapping("/{postUuid}")
    fun deletePost(@PathVariable postUuid: UUID) {
        postService.deletePost(postUuid)
    }
}
