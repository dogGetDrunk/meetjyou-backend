package com.dogGetDrunk.meetjyou.terms.dto

import com.dogGetDrunk.meetjyou.terms.TermsType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

data class PublishTermsRequest(
    @field:NotNull(message = "약관 타입은 비어 있을 수 없습니다.")
    val type: TermsType,

    @field:NotBlank(message = "버전은 비어 있을 수 없습니다.")
    @field:Size(max = 20, message = "버전은 20자 이내여야 합니다.")
    val version: String,

    @field:NotBlank(message = "노출 텍스트는 비어 있을 수 없습니다.")
    @field:Size(max = 255, message = "노출 텍스트는 255자 이내여야 합니다.")
    val displayText: String,

    val required: Boolean,

    @field:NotBlank(message = "본문 해시는 비어 있을 수 없습니다.")
    @field:Size(max = 64, message = "본문 해시는 64자 이내여야 합니다.")
    val contentHash: String,

    val effectiveAt: Instant? = null,
)
