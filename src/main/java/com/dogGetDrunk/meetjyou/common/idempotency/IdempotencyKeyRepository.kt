package com.dogGetDrunk.meetjyou.common.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IdempotencyKeyRepository : JpaRepository<IdempotencyKey, Long> {
    fun findByScopeAndUser_IdAndIdempotencyKey(
        scope: IdempotencyScope,
        userId: Long,
        idempotencyKey: String,
    ): IdempotencyKey?
}
