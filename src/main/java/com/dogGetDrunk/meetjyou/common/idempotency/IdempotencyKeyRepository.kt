package com.dogGetDrunk.meetjyou.common.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface IdempotencyKeyRepository : JpaRepository<IdempotencyKey, Long> {
    fun findByScopeAndUser_IdAndIdempotencyKey(
        scope: IdempotencyScope,
        userId: Long,
        idempotencyKey: String,
    ): IdempotencyKey?

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.user.id IN :userIds")
    fun deleteAllByUserIdIn(@Param("userIds") userIds: Collection<Long>): Int
}
