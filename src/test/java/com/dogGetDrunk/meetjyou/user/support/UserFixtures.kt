package com.dogGetDrunk.meetjyou.user.support

import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import java.time.LocalDate

object UserFixtures {
    fun email(i: Int = 1) = "user$i@example.com"
    fun nickname(i: Int = 1) = "user$i"
    fun birthDate(y: Int = 2000, m: Int = 1, d: Int = 1): LocalDate = LocalDate.of(y, m, d)
    fun authProvider() = AuthProvider.KAKAO
    fun externalId(i: Int = 1) = "external-$i"
    fun user(
        email: String = email(),
        nickname: String = nickname(),
        authProvider: AuthProvider = authProvider(),
        externalId: String = externalId(),
    ) = User(email = email, nickname = nickname, authProvider = authProvider, externalId = externalId)
}
