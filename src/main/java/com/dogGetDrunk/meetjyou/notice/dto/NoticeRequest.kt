package com.dogGetDrunk.meetjyou.notice.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class NoticeRequest(
    @field:NotBlank(message = "제목은 비어 있을 수 없습니다.")
    @field:Size(max = 50, message = "제목은 50자 이내여야 합니다.")
    val title: String,

    @field:NotBlank(message = "본문은 비어 있을 수 없습니다.")
    @field:Size(max = 1000, message = "본문은 1000자 이내여야 합니다.")
    val body: String
)
