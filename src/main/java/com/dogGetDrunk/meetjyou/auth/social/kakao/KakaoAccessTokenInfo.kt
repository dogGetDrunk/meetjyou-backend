package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

// Kakao responds in snake_case (app_id); without the naming strategy appId silently binds to 0.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoAccessTokenInfo(
    val id: Long,
    val appId: Long,
)
