package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.plan.dto.CreateMarkerRequest
import com.dogGetDrunk.meetjyou.plan.dto.MarkerResponse
import com.dogGetDrunk.meetjyou.plan.dto.UpdateMarkerRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/plans/{planUuid}/markers")
@Tag(name = "마커 API", description = "여행 계획 내 마커 생성, 조회, 수정, 삭제 기능을 제공합니다.")
class MarkerController(
    private val markerService: MarkerService,
) {

    @Operation(summary = "마커 생성", description = "여행 계획에 마커를 추가합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "생성 성공",
                content = [Content(schema = Schema(implementation = MarkerResponse::class))]
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "플랜을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping
    fun createMarker(
        @PathVariable planUuid: UUID,
        @RequestBody request: CreateMarkerRequest,
    ): MarkerResponse {
        return markerService.createMarker(planUuid, request)
    }

    @Operation(summary = "마커 목록 조회", description = "여행 계획의 마커 전체를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "404",
                description = "플랜을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @GetMapping
    fun getMarkersByPlan(@PathVariable planUuid: UUID): List<MarkerResponse> {
        return markerService.getMarkersByPlan(planUuid)
    }

    @Operation(summary = "마커 단건 조회", description = "마커 UUID로 단건 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = MarkerResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "마커 또는 플랜을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @GetMapping("/{markerUuid}")
    fun getMarkerByUuid(
        @PathVariable planUuid: UUID,
        @PathVariable markerUuid: UUID,
    ): MarkerResponse {
        return markerService.getMarkerByUuid(planUuid, markerUuid)
    }

    @Operation(summary = "마커 수정", description = "마커 정보를 수정합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "수정 성공",
                content = [Content(schema = Schema(implementation = MarkerResponse::class))]
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "마커 또는 플랜을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PutMapping("/{markerUuid}")
    fun updateMarker(
        @PathVariable planUuid: UUID,
        @PathVariable markerUuid: UUID,
        @RequestBody request: UpdateMarkerRequest,
    ): MarkerResponse {
        return markerService.updateMarker(planUuid, markerUuid, request)
    }

    @Operation(summary = "마커 삭제", description = "마커를 삭제합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "삭제 성공"),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "마커 또는 플랜을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @DeleteMapping("/{markerUuid}")
    fun deleteMarker(
        @PathVariable planUuid: UUID,
        @PathVariable markerUuid: UUID,
    ) {
        markerService.deleteMarker(planUuid, markerUuid)
    }
}
