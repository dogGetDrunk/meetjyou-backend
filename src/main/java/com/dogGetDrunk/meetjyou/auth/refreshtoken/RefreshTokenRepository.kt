package com.dogGetDrunk.meetjyou.auth.refreshtoken

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByJti(jti: String): RefreshToken?
}
