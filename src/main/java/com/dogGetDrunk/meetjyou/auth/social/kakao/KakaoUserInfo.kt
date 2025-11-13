package com.dogGetDrunk.meetjyou.auth.social.kakao

import java.time.LocalDateTime

data class KakaoUserInfo(
    val id: Long,
    val connectedAt: LocalDateTime,
    val kakaoAccount: KakaoAccount,
)
