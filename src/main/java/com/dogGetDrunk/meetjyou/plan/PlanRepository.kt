package com.dogGetDrunk.meetjyou.plan

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PlanRepository : JpaRepository<Plan, Long> {
    fun findByUuid(uuid: UUID): Plan?
    fun findAllByOwner_Uuid(ownerUuid: UUID, pageable: Pageable): Page<Plan>
    fun existsByUuid(uuid: UUID): Boolean

    // Locking read (SELECT ... FOR UPDATE): concurrent marker replacements of the same plan wait
    // here. Make it the first read of the transaction: an entity already in the persistence context
    // is returned as is, and an earlier plain read would pin the REPEATABLE READ snapshot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Plan p WHERE p.uuid = :uuid")
    fun findForUpdateByUuid(@Param("uuid") uuid: UUID): Plan?
}
