package com.dogGetDrunk.meetjyou.auth.refreshtoken

import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    // Locking read (SELECT ... FOR UPDATE): concurrent rotations or logouts of the same token wait
    // here and then see the latest committed row, not their snapshot. Call it before the row is
    // loaded by any other query in the transaction: an entity already in the persistence context
    // is returned as is, without its state being refreshed.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByJti(jti: String): RefreshToken?

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.user = :user")
    fun revokeAllByUser(user: User): Int

    @Modifying
    @Query("delete from RefreshToken r where r.user.id in :userIds")
    fun deleteAllByUserIdIn(@Param("userIds") userIds: Collection<Long>): Int
}
