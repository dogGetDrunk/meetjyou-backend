package com.dogGetDrunk.meetjyou.party

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PartyRepository : JpaRepository<Party, Long> {
    fun findByUuid(uuid: UUID): Party?
    fun findAllByPlan_Uuid(planUuid: UUID, pageable: Pageable): Page<Party>
}
