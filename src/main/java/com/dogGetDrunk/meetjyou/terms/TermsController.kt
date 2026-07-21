package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.terms.dto.GetTermsContentUrlResponse
import com.dogGetDrunk.meetjyou.terms.dto.GetTermsResponse
import com.dogGetDrunk.meetjyou.terms.dto.PublishTermsRequest
import com.dogGetDrunk.meetjyou.terms.dto.TermsUploadPar
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import org.springframework.web.bind.annotation.RequestMapping

@RestControllerV1
@RequestMapping("/terms")
@Tag(name = "약관 API", description = "약관 조회 및 약관 본문 다운로드 URL 관련 API를 제공합니다.")
class TermsController(
    private val termsService: TermsService,
) {
    @Operation(
        summary = "활성 약관 목록 조회",
        description = "회원가입 화면에 노출할 활성 약관 목록을 조회합니다.",
    )
    @GetMapping("/active")
    fun getAllActiveTerms(): List<GetTermsResponse> {
        return termsService.getAllActiveTerms()
    }

    @Operation(
        summary = "약관 본문 다운로드 PAR URL 조회",
        description = "특정 약관의 본문 파일 다운로드를 위한 OCI PAR URL을 발급합니다.",
    )
    @GetMapping("/{termsUuid}/content-url")
    fun getTermsContentUrl(
        @PathVariable termsUuid: String,
    ): GetTermsContentUrlResponse {
        return termsService.getTermsContentUrl(termsUuid)
    }

    @Operation(
        summary = "[admin] 약관 본문 업로드 PAR URL 발급",
        description = "약관 본문 파일을 OCI에 업로드하기 위한 오브젝트 키와 PAR URL을 발급합니다. " +
            "오브젝트 키는 타입과 버전으로 결정되며, 업로드 완료 후 동일한 타입·버전으로 약관 게시를 요청해야 합니다.",
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/content/par/upload")
    fun createTermsContentUploadPar(
        @RequestParam type: TermsType,
        @RequestParam version: String,
    ): TermsUploadPar {
        return termsService.createContentUploadPar(type, version)
    }

    @Operation(
        summary = "[admin] 약관 게시",
        description = "새로운 약관 버전을 게시합니다. 타입·버전으로 결정되는 본문 오브젝트가 OCI에 실제로 업로드되었는지와 " +
            "해시가 일치하는지 검증하며, 동일 타입의 기존 활성 약관을 대체하고 이전 약관에 동의했던 사용자에게 재동의 알림을 발송합니다. " +
            "동일한 타입·버전으로 이미 게시된 약관이 있으면 409를 반환하며, 이 경우 새 약관 저장이나 재동의 알림 발송은 일어나지 않습니다.",
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping
    fun publishTerms(
        @RequestBody @Valid request: PublishTermsRequest,
    ): GetTermsResponse {
        return termsService.publishTerms(request)
    }
}
