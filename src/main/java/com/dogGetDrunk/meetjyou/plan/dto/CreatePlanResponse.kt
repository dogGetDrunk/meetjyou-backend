package com.dogGetDrunk.meetjyou.plan.dto

import com.dogGetDrunk.meetjyou.plan.Plan
import java.time.Instant
import java.util.UUID

data class CreatePlanResponse(
    val uuid: UUID,
    val itinStart: Instant,
    val itinFinish: Instant,
    val destination: String,
    val centerLat: Double,
    val centerLng: Double,
    val memo: String?,
    val userUuid: UUID
) {
    companion object {
        fun of(plan: Plan) = CreatePlanResponse(
            uuid = plan.uuid,
            itinStart = plan.itinStart,
            itinFinish = plan.itinFinish,
            destination = plan.location,
            centerLat = plan.centerLat,
            centerLng = plan.centerLng,
            memo = plan.memo,
            userUuid = plan.owner.uuid,
        )
    }
}
