package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.jwt.UserContext
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanRequest
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanResponse
import com.dogGetDrunk.meetjyou.plan.dto.GetPlanResponse
import com.dogGetDrunk.meetjyou.plan.dto.UpdatePlanRequest
import com.dogGetDrunk.meetjyou.plan.dto.UpdatePlanResponse
import com.dogGetDrunk.meetjyou.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PlanService(
    private val planRepository: PlanRepository,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(PlanService::class.java)

    @Transactional
    fun createPlan(request: CreatePlanRequest): CreatePlanResponse {
        val user = UserContext.getUser()

        val plan = Plan(
            itinStart = request.itinStart,
            itinFinish = request.itinFinish,
            location = request.location,
            centerLat = request.centerLat,
            centerLng = request.centerLng,
            memo = request.memo,
            owner = user
        )

        planRepository.save(plan)
        log.info("New plan created: $plan")

        return CreatePlanResponse.of(plan)
    }

    @Transactional(readOnly = true)
    fun getPlanByUuid(planUuid: UUID): GetPlanResponse {
        val plan = planRepository.findByUuid(planUuid)
            ?: throw PlanNotFoundException(planUuid)

        return GetPlanResponse.of(plan)
    }

    @Transactional(readOnly = true)
    fun getPlansByUserUuid(userUuid: UUID, pageable: Pageable): Page<GetPlanResponse> {
        if (!userRepository.existsByUuid(userUuid)) {
            throw UserNotFoundException(userUuid)
        }

        return planRepository.findAllByOwner_Uuid(userUuid, pageable)
            .map { GetPlanResponse.of(it) }
    }

    @Transactional
    fun updatePlan(planUuid: UUID, request: UpdatePlanRequest): UpdatePlanResponse {
        val plan = planRepository.findByUuid(planUuid)
            ?: throw PlanNotFoundException(planUuid)

        plan.apply {
            itinStart = request.itinStart
            itinFinish = request.itinFinish
            location = request.location
            centerLat = request.centerLat
            centerLng = request.centerLng
            memo = request.memo
        }

        log.info("Plan updated: $plan")
        return UpdatePlanResponse.of(plan)
    }

    @Transactional
    fun deletePlan(planUuid: UUID) {
        val plan = planRepository.findByUuid(planUuid)
            ?: throw PlanNotFoundException(planUuid)

        planRepository.delete(plan)
        log.info("Plan deleted: uuid=$planUuid")
    }
}
