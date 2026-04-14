package com.dogGetDrunk.meetjyou.user.support

import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User

object UserFixtures {
    fun email(i: Int = 1) = "user$i@example.com"
    fun nickname(i: Int = 1) = "user$i"
    fun authProvider() = AuthProvider.KAKAO
    fun externalId(i: Int = 1) = "external-$i"

    fun user(
        email: String = email(),
        nickname: String = nickname(),
        authProvider: AuthProvider = authProvider(),
        externalId: String = externalId(),
    ) = User(email = email, nickname = nickname, authProvider = authProvider, externalId = externalId)
}
