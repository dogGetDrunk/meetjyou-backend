package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanRequest
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanResponse
import com.dogGetDrunk.meetjyou.plan.dto.GetPlanResponse
import com.dogGetDrunk.meetjyou.plan.dto.UpdatePlanRequest
import com.dogGetDrunk.meetjyou.plan.dto.UpdatePlanResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
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
@RequestMapping("/api/v1/plans")
@Tag(name = "여행 계획 API", description = "여행 계획 생성, 조회, 수정, 삭제 기능을 제공합니다.")
class PlanController(
    private val planService: PlanService,
) {

    @Operation(summary = "여행 계획 생성", description = "새로운 여행 계획을 생성합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "생성 성공",
                content = [Content(schema = Schema(implementation = CreatePlanResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping
    fun createPlan(@RequestBody request: CreatePlanRequest): CreatePlanResponse {
        return planService.createPlan(request)
    }

    @Operation(summary = "단건 조회", description = "UUID로 여행 계획을 조회합니다.")
    @GetMapping("/{planUuid}")
    fun getPlanByUuid(@PathVariable planUuid: UUID): GetPlanResponse {
        return planService.getPlanByUuid(planUuid)
    }

    @Operation(summary = "작성자 기준 조회", description = "작성자 UUID로 여행 계획을 조회합니다.")
    @GetMapping("/user/{userUuid}")
    fun getPlansByUser(
        @PathVariable userUuid: UUID,
        @ParameterObject
        @PageableDefault(size = 10, sort = ["itinStart"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<GetPlanResponse> {
        return planService.getPlansByUserUuid(userUuid, pageable)
    }

    @Operation(summary = "여행 계획 수정", description = "여행 계획을 수정합니다.")
    @PutMapping("/{planUuid}")
    fun updatePlan(@PathVariable planUuid: UUID, @RequestBody request: UpdatePlanRequest): UpdatePlanResponse {
        return planService.updatePlan(planUuid, request)
    }

    @Operation(summary = "여행 계획 삭제", description = "UUID로 여행 계획을 삭제합니다.")
    @DeleteMapping("/{planUuid}")
    fun deletePlan(@PathVariable planUuid: UUID) {
        planService.deletePlan(planUuid)
    }
}
