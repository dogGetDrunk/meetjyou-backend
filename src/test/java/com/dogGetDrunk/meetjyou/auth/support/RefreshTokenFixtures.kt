package com.dogGetDrunk.meetjyou.auth.support

import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshToken
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import java.time.LocalDateTime
import java.util.UUID

object RefreshTokenFixtures {
    fun refreshToken(
        user: User = UserFixtures.user(),
        jti: String = UUID.randomUUID().toString(),
        expiresAt: LocalDateTime = LocalDateTime.now().plusDays(30),
        revoked: Boolean = false,
    ) = RefreshToken(jti = jti, user = user, expiresAt = expiresAt, revoked = revoked)
}
