package com.dogGetDrunk.meetjyou.user.support

import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import java.time.Instant

object UserFixtures {
    fun email(i: Int = 1) = "user$i@example.com"
    fun nickname(i: Int = 1) = "user$i"
    fun authProvider() = AuthProvider.KAKAO
    fun externalId(i: Int = 1) = "external-$i"

    fun user(
        email: String? = email(),
        nickname: String = nickname(),
        authProvider: AuthProvider = authProvider(),
        externalId: String = externalId(),
    ) = User(email = email, nickname = nickname, authProvider = authProvider, externalId = externalId)

    /** Withdrawn but still in the grace period (DELETED), with a profile photo. */
    fun gracedUser(i: Int): User {
        val user = user(email = email(i), nickname = nickname(i), externalId = externalId(i))
        user.hasProfileImage = true
        user.withdraw(Instant.now())
        return user
    }

    /** Grace period over and anonymized (PURGED); had a profile photo before the purge. */
    fun purgedUser(i: Int): User {
        val user = gracedUser(i)
        user.anonymize()
        return user
    }
}
