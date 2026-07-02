package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.party.dto.GetPartyResponse
import com.dogGetDrunk.meetjyou.party.dto.GetPendingJoinRequestsResponse
import com.dogGetDrunk.meetjyou.party.dto.JoinPartyRequest
import com.dogGetDrunk.meetjyou.party.dto.JoinPartyResponse
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.UUID

@RestControllerV1
@RequestMapping("/parties")
@Tag(name = "파티 API", description = "여행 파티 생성, 조회, 수정, 삭제 기능 제공")
class PartyController(
    private val partyService: PartyService,
) {

//    @Operation(summary = "파티 생성", description = "새로운 파티를 생성합니다.")
//    @ApiResponses(
//        value = [
//            ApiResponse(
//                responseCode = "201",
//                description = "파티 생성 성공",
//                content = [Content(schema = Schema(implementation = CreatePartyResponse::class))]
//            ),
//            ApiResponse(
//                responseCode = "400",
//                description = "잘못된 요청 형식",
//                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
//            )
//        ]
//    )
//    @PostMapping
//    @ResponseStatus(HttpStatus.CREATED)
//    fun createParty(@RequestBody request: CreatePartyRequest): CreatePartyResponse {
//        return partyService.createParty(request)
//    }

    @Operation(summary = "파티 단건 조회", description = "파티 UUID로 특정 파티를 조회합니다.")
    @GetMapping("/{partyUuid}")
    fun getPartyByUuid(@PathVariable partyUuid: UUID): GetPartyResponse {
        return partyService.getPartyByUuid(partyUuid)
    }

    @Operation(summary = "전체 파티 조회", description = "모든 파티 목록을 페이지네이션하여 조회합니다.")
    @GetMapping
    fun getAllParties(
        @ParameterObject @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<GetPartyResponse> {
        return partyService.getAllParties(pageable)
    }

    @Operation(summary = "유저가 참여 중인 파티 목록 조회", description = "유저 UUID로 특정 유저가 참여 중인 모든 파티를 조회합니다.")
    @GetMapping("/user/{userUuid}")
    fun getPartiesByUserUuid(
        @PathVariable userUuid: UUID,
        @ParameterObject @PageableDefault(size = 10, sort = ["party.createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<GetPartyResponse> {
        return partyService.getPartiesByUserUuid(userUuid, pageable)
    }

    @Operation(summary = "여행 계획서 UUID 기반 파티 조회", description = "여행 계획서 UUID로 특정 여행 계획서와 연결된 모든 파티를 조회합니다.")
    @GetMapping("/plan/{planUuid}")
    fun getPartiesByPlanUuid(
        @PathVariable planUuid: UUID,
        @ParameterObject @PageableDefault(size = 10, sort = ["party.createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<GetPartyResponse> {
        return partyService.getPartiesByPlanUuid(planUuid, pageable)
    }

    @Operation(summary = "파티 수정", description = "파티 UUID로 특정 파티를 수정합니다.")
    @PutMapping("/{partyUuid}")
    fun updateParty(
        @PathVariable partyUuid: UUID,
        @Valid @RequestBody request: UpdatePartyRequest,
    ): UpdatePartyResponse {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        return partyService.updateParty(partyUuid, userUuid, request)
    }

    @Operation(summary = "파티 가입 신청", description = "현재 로그인한 유저가 파티 가입을 신청합니다. 호스트 승인 후 참여가 확정됩니다.")
    @PostMapping("/{partyUuid}/join-requests")
    fun requestJoinParty(
        @PathVariable partyUuid: UUID,
        @Valid @RequestBody(required = false) request: JoinPartyRequest?,
    ): ResponseEntity<JoinPartyResponse> {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(partyService.requestJoinParty(partyUuid, userUuid, request?.applicationNote))
    }

    @Operation(summary = "파티 이미지 업로드 확인", description = "OCI에 파티 이미지 업로드를 마친 뒤 HOST가 호출하여 파티 전용 이미지로 전환합니다.")
    @PutMapping("/{partyUuid}/img/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirmPartyImage(@PathVariable partyUuid: UUID) {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        partyService.confirmPartyImage(partyUuid, userUuid)
    }

    @Operation(summary = "파티 가입 신청 취소", description = "현재 로그인한 유저가 PENDING 상태인 자신의 파티 가입 신청을 취소합니다.")
    @DeleteMapping("/{partyUuid}/join-requests/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelJoinRequest(@PathVariable partyUuid: UUID) {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        partyService.cancelJoinRequest(partyUuid, userUuid)
    }

    @Operation(summary = "파티 종료", description = "HOST가 파티를 종료하고 연결된 모집글을 마감 처리합니다.")
    @PostMapping("/{partyUuid}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun completeParty(@PathVariable partyUuid: UUID) {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        partyService.completeParty(partyUuid, userUuid)
    }

    @Operation(summary = "파티원 강퇴", description = "HOST가 특정 파티원을 강퇴합니다.")
    @PostMapping("/{partyUuid}/members/{targetUserUuid}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun banMember(
        @PathVariable partyUuid: UUID,
        @PathVariable targetUserUuid: UUID,
    ) {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        partyService.banMember(partyUuid, userUuid, targetUserUuid)
    }

    @Operation(summary = "파티 탈퇴", description = "MEMBER가 현재 파티에서 탈퇴합니다.")
    @PostMapping("/{partyUuid}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveParty(@PathVariable partyUuid: UUID) {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        partyService.leaveParty(partyUuid, userUuid)
    }

    @Operation(summary = "파티 삭제", description = "파티 UUID로 특정 파티를 삭제합니다.")
    @DeleteMapping("/{partyUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteParty(@PathVariable partyUuid: UUID) {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        partyService.deleteParty(partyUuid, userUuid)
    }

    @Operation(summary = "참여 신청 목록 조회", description = "HOST가 대기 중인 참여 신청 목록을 조회합니다.")
    @GetMapping("/{partyUuid}/join-requests")
    fun getPendingJoinRequests(@PathVariable partyUuid: UUID): GetPendingJoinRequestsResponse {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        return partyService.getPendingJoinRequests(partyUuid, userUuid)
    }

    @Operation(summary = "참여 신청 승인", description = "HOST가 특정 유저의 참여 신청을 승인합니다.")
    @PostMapping("/{partyUuid}/join-requests/{applicantUuid}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun approveJoinRequest(
        @PathVariable partyUuid: UUID,
        @PathVariable applicantUuid: UUID,
    ) {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        partyService.approveJoinRequest(partyUuid, userUuid, applicantUuid)
    }

    @Operation(summary = "참여 신청 거절", description = "HOST가 특정 유저의 참여 신청을 거절합니다.")
    @PostMapping("/{partyUuid}/join-requests/{applicantUuid}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun rejectJoinRequest(
        @PathVariable partyUuid: UUID,
        @PathVariable applicantUuid: UUID,
    ) {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        partyService.rejectJoinRequest(partyUuid, userUuid, applicantUuid)
    }
}
