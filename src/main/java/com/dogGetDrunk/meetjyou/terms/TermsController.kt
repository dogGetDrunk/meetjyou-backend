package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.terms.dto.GetTermsContentUrlResponse
import com.dogGetDrunk.meetjyou.terms.dto.GetTermsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/terms")
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
}
