package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.plan.Plan
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PartyRepository : JpaRepository<Party, Long> {
    fun findByUuid(uuid: UUID): Party?
    fun findAllByUuidIn(uuids: List<UUID>): List<Party>
    fun findAllByPlan_Uuid(planUuid: UUID, pageable: Pageable): Page<Party>

    // Bulk on purpose, same as PostRepository.detachPlan: skips a party re-linked after the caller's snapshot (#201).
    @Modifying
    @Query("UPDATE Party p SET p.plan = null WHERE p.plan = :plan")
    fun detachPlan(@Param("plan") plan: Plan): Int
    fun existsByPlan_Uuid(planUuid: UUID): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Party p WHERE p.uuid = :uuid")
    fun findByUuidForUpdate(@Param("uuid") uuid: UUID): Party?

    // Scalar on purpose: resolves which party row to lock without loading the post or party into
    // the persistence context ahead of that lock.
    @Query("SELECT p.party.uuid FROM Post p WHERE p.uuid = :postUuid")
    fun findUuidByPostUuid(@Param("postUuid") postUuid: UUID): UUID?

    @Query(
        value = "SELECT p FROM Party p LEFT JOIN FETCH p.plan",
        countQuery = "SELECT COUNT(p) FROM Party p",
    )
    fun findAllWithPlan(pageable: Pageable): Page<Party>

    @Query(
        value = "SELECT p FROM Party p LEFT JOIN FETCH p.plan WHERE p.plan.uuid = :planUuid",
        countQuery = "SELECT COUNT(p) FROM Party p WHERE p.plan.uuid = :planUuid",
    )
    fun findAllByPlanUuidWithPlan(@Param("planUuid") planUuid: UUID, pageable: Pageable): Page<Party>
}
