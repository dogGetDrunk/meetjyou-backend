package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyResponse
import com.dogGetDrunk.meetjyou.party.dto.GetPartyResponse
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyResponse
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
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/parties")
@Tag(name = "파티 API", description = "여행 파티 생성, 조회, 수정, 삭제 기능 제공")
class PartyController(
    private val partyService: PartyService,
) {

    @Operation(summary = "파티 생성", description = "새로운 파티를 생성합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "파티 생성 성공",
                content = [Content(schema = Schema(implementation = CreatePartyResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 형식",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createParty(@RequestBody request: CreatePartyRequest): CreatePartyResponse {
        return partyService.createParty(request)
    }

    @Operation(summary = "파티 단건 조회", description = "UUID로 특정 파티를 조회합니다.")
    @ApiResponse(
        responseCode = "200",
        description = "파티 조회 성공",
        content = [Content(schema = Schema(implementation = GetPartyResponse::class))]
    )
    @GetMapping("/{partyUuid}")
    fun getPartyByUuid(@PathVariable partyUuid: UUID): GetPartyResponse {
        return partyService.getPartyByUuid(partyUuid)
    }

    @Operation(summary = "전체 파티 조회", description = "모든 파티 목록을 페이지네이션하여 조회합니다.")
    @GetMapping
    fun getAllParties(
        @ParameterObject
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<GetPartyResponse> {
        return partyService.getAllParties(pageable)
    }

    @Operation(summary = "유저가 참여 중인 파티 목록 조회", description = "UUID로 특정 유저가 참여 중인 모든 파티를 조회합니다.")
    @GetMapping("/user/{userUuid}")
    fun getPartiesByUserUuid(
        @PathVariable userUuid: UUID,
        @ParameterObject @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<GetPartyResponse> {
        return partyService.getPartiesByUserUuid(userUuid, pageable)
    }

    @Operation(summary = "여행 계획서 UUID 기반 파티 조회", description = "특정 여행 계획서에 속한 파티 목록을 조회합니다.")
    @GetMapping("/plan/{planUuid}")
    fun getPartiesByPlanUuid(
        @PathVariable planUuid: UUID,
        @ParameterObject
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<GetPartyResponse> {
        return partyService.getPartiesByPlanUuid(planUuid, pageable)
    }

    @Operation(summary = "파티 수정", description = "UUID로 특정 파티를 수정합니다.")
    @PutMapping("/{partyUuid}")
    fun updateParty(
        @PathVariable partyUuid: UUID,
        @RequestBody request: UpdatePartyRequest,
    ): UpdatePartyResponse {
        return partyService.updateParty(partyUuid, request)
    }

    @Operation(summary = "파티 삭제", description = "UUID로 특정 파티를 삭제합니다.")
    @DeleteMapping("/{partyUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteParty(@PathVariable partyUuid: UUID) {
        partyService.deleteParty(partyUuid)
    }
}
