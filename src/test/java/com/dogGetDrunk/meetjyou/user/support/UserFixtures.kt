package com.dogGetDrunk.meetjyou.user.support

import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import java.time.LocalDate

object UserFixtures {
    fun email(i: Int = 1) = "user$i@example.com"
    fun nickname(i: Int = 1) = "user$i"
    fun birthDate(y: Int = 2000, m: Int = 1, d: Int = 1): LocalDate = LocalDate.of(y, m, d)
    fun authProvider() = AuthProvider.KAKAO
    fun user(
        email: String = email(),
        nickname: String = nickname(),
        birthDate: LocalDate = birthDate(),
        authProvider: AuthProvider = authProvider()
    ) = User(email = email, nickname = nickname, birthDate = birthDate, authProvider = authProvider)
}
