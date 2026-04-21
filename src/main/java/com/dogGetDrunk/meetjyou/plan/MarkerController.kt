package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.plan.dto.MarkerResponse
import com.dogGetDrunk.meetjyou.plan.dto.ReplaceMarkersRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/plans/{planUuid}/markers")
@Tag(name = "마커 API", description = "여행 계획 내 마커 조회, 일괄 교체 기능을 제공합니다.")
class MarkerController(
    private val markerService: MarkerService,
) {

    @Operation(summary = "마커 목록 조회", description = "여행 계획의 마커 전체를 dayNum, idx 순으로 조회합니다.")
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

    @Operation(summary = "마커 일괄 교체", description = "여행 계획의 마커 전체를 새 목록으로 교체합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "교체 성공"),
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
    @PutMapping
    fun replaceMarkers(
        @PathVariable planUuid: UUID,
        @RequestBody request: ReplaceMarkersRequest,
    ): List<MarkerResponse> {
        return markerService.replaceMarkers(planUuid, request.markers)
    }
}
