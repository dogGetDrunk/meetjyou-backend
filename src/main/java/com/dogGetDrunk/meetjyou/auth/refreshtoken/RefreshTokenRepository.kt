package com.dogGetDrunk.meetjyou.auth.refreshtoken

import com.dogGetDrunk.meetjyou.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByJti(jti: String): RefreshToken?

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.user = :user")
    fun revokeAllByUser(user: User): Int

    @Modifying
    @Query("delete from RefreshToken r where r.user.id in :userIds")
    fun deleteAllByUserIdIn(@Param("userIds") userIds: Collection<Long>): Int
}
