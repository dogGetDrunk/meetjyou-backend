package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

// Kakao responds in snake_case (kakao_account).
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoUserInfo(
    val id: Long,
    val kakaoAccount: KakaoAccount,
)
