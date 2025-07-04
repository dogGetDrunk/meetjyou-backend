package com.dogGetDrunk.meetjyou.plan.dto

import com.dogGetDrunk.meetjyou.plan.Plan
import java.time.LocalDateTime

data class UpdatePlanResponse(
    val uuid: String,
    val itinStart: LocalDateTime,
    val itinFinish: LocalDateTime,
    val destination: String,
    val centerLat: Double,
    val centerLng: Double,
    val memo: String?,
    val userUuid: String
) {
    companion object {
        fun of(plan: Plan) = UpdatePlanResponse(
            uuid = plan.uuid.toString(),
            itinStart = plan.itinStart,
            itinFinish = plan.itinFinish,
            destination = plan.location,
            centerLat = plan.centerLat,
            centerLng = plan.centerLng,
            memo = plan.memo,
            userUuid = plan.user.uuid.toString()
        )
    }
}
