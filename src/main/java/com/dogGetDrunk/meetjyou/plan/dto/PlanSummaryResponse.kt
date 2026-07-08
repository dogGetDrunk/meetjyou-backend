package com.dogGetDrunk.meetjyou.plan.dto

import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.plan.PlanRecruitStatus
import java.time.Instant
import java.util.UUID

data class PlanSummaryResponse(
    val uuid: UUID,
    val title: String,
    val lastModifiedAt: Instant,
    val status: PlanRecruitStatus?,
    val favorite: Boolean,
) {
    companion object {
        fun of(plan: Plan, status: PlanRecruitStatus?) = PlanSummaryResponse(
            uuid = plan.uuid,
            title = plan.title,
            lastModifiedAt = plan.updatedAt,
            status = status,
            favorite = plan.favorite,
        )
    }
}
