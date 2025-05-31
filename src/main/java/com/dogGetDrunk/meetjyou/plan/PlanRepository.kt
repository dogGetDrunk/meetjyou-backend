package com.dogGetDrunk.meetjyou.plan

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PlanRepository : JpaRepository<Plan, Long> {
    fun findByUuid(uuid: UUID): Plan?
    fun findAllByUser_Uuid(userUuid: UUID, pageable: Pageable): Page<Plan>
}
