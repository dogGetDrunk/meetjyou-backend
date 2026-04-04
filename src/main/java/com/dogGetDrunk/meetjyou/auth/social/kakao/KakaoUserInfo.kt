package com.dogGetDrunk.meetjyou.auth.social.kakao

import java.time.Instant

data class KakaoUserInfo(
    val id: Long,
    val connectedAt: Instant,
    val kakaoAccount: KakaoAccount,
)
