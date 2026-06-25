package com.dogGetDrunk.meetjyou.party

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PartyRepository : JpaRepository<Party, Long> {
    fun findByUuid(uuid: UUID): Party?
    fun findAllByPlan_Uuid(planUuid: UUID, pageable: Pageable): Page<Party>
    fun existsByPlan_Uuid(planUuid: UUID): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Party p WHERE p.uuid = :uuid")
    fun findByUuidForUpdate(@Param("uuid") uuid: UUID): Party?
}
