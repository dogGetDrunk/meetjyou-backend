package com.dogGetDrunk.meetjyou.plan

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MarkerRepository : JpaRepository<Marker, Long> {
    fun findByUuid(uuid: UUID): Marker?
    fun findAllByPlan_UuidOrderByDayNumAscIdxAsc(planUuid: UUID): List<Marker>
    fun deleteAllByPlan(plan: Plan)
}
