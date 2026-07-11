package com.dogGetDrunk.meetjyou.plan

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MarkerRepository : JpaRepository<Marker, Long> {
    fun findByUuid(uuid: UUID): Marker?
    fun findAllByPlan_UuidOrderByDayNumAscIdxAsc(planUuid: UUID): List<Marker>
    fun findAllByPlan_UuidIn(planUuids: List<UUID>): List<Marker>
    fun findAllByPlan_UuidInOrderByDayNumAscIdxAsc(planUuids: List<UUID>): List<Marker>

    // NOTE: tried a bulk `@Modifying @Query("DELETE ... WHERE m.plan = :plan")` here to avoid the
    // row-by-row derived delete, but under concurrent load it caused InnoDB gap-lock deadlocks
    // against the immediately-following saveAll() insert on the same plan_id range (confirmed via
    // k6 load test: 19 "Deadlock found when trying to get lock" 500s on PUT .../markers). Reverted
    // — marker counts per plan are small, so the row-by-row delete's extra statements are cheap
    // compared to the deadlock risk of a bulk delete sharing a transaction with a same-key insert.
    fun deleteAllByPlan(plan: Plan)
}
