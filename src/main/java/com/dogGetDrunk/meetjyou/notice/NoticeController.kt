package com.dogGetDrunk.meetjyou.notice

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.notice.dto.NoticeRequest
import com.dogGetDrunk.meetjyou.notice.dto.NoticeResponse
import com.dogGetDrunk.meetjyou.notice.dto.ResendNoticeBroadcastRequest
import com.dogGetDrunk.meetjyou.notification.broadcast.dto.BroadcastResultResponse
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
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID

@RestControllerV1
@RequestMapping("/notices")
@Tag(name = "공지사항 API")
class NoticeController(
    private val noticeService: NoticeService,
) {

    @Operation(summary = "모든 공지사항 조회", description = "등록된 모든 공지사항을 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(type = "array", implementation = NoticeResponse::class)
                )]
            )
        ]
    )
    @GetMapping
    fun getAllNotices(
        @ParameterObject
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ResponseEntity<Page<NoticeResponse>> {
        val result = noticeService.getAllNotices(pageable)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "공지사항 단건 조회", description = "공지사항 UUID로 공지사항을 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = NoticeResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "공지사항을 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            )
        ]
    )
    @GetMapping("/{uuid}")
    fun getNoticeByUuid(@PathVariable uuid: UUID): ResponseEntity<NoticeResponse> {
        val notice = noticeService.getNoticeByUuid(uuid)
        return ResponseEntity.ok(notice)
    }

    @Operation(summary = "[admin] 공지사항 생성", description = "새로운 공지사항을 생성합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "생성 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = NoticeResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 데이터",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            )
        ]
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping
    fun createNotice(@RequestBody @Valid request: NoticeRequest): ResponseEntity<NoticeResponse> {
        val created = noticeService.createNotice(request)
        return ResponseEntity.ok(created)
    }

    @Operation(summary = "[admin] 공지사항 수정", description = "공지사항의 제목과 본문을 수정합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "수정 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = NoticeResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 데이터",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "공지사항을 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            )
        ]
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @PutMapping("/{uuid}")
    fun updateNotice(
        @PathVariable uuid: UUID,
        @RequestBody @Valid request: NoticeRequest,
    ): ResponseEntity<NoticeResponse> {
        val updated = noticeService.updateNotice(uuid, request)
        return ResponseEntity.ok(updated)
    }

    @Operation(
        summary = "[admin] 공지사항 알림 재발송",
        description = "알림 발송이 누락된 공지사항을 현재 수신 설정 기준으로 발송합니다. " +
            "이미 발송된 공지사항이면 409(BROADCAST_ALREADY_SENT)를 반환합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "발송 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = BroadcastResultResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "공지사항을 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 발송된 공지사항",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            )
        ]
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/{uuid}/broadcast")
    fun resendBroadcast(
        @PathVariable uuid: UUID,
        @RequestBody request: ResendNoticeBroadcastRequest,
    ): ResponseEntity<BroadcastResultResponse> {
        return ResponseEntity.ok(noticeService.resendBroadcast(uuid, request))
    }

    @Operation(summary = "[admin] 공지사항 삭제", description = "공지사항을 삭제합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "삭제 성공"),
            ApiResponse(
                responseCode = "404",
                description = "공지사항을 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )]
            )
        ]
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @DeleteMapping("/{uuid}")
    fun deleteNotice(@PathVariable uuid: UUID) {
        noticeService.deleteNotice(uuid)
    }
}
