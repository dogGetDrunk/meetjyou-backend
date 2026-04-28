package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanReadAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
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
    private val markerRepository: MarkerRepository,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val userPartyRepository: UserPartyRepository,
) {
    private val log = LoggerFactory.getLogger(PlanService::class.java)

    @Transactional
    fun createPlan(request: CreatePlanRequest): CreatePlanResponse {
        val currentUserUuid = SecurityUtil.getCurrentUserUuid()
        val user = userRepository.findByUuid(currentUserUuid)
            ?: throw UserNotFoundException(currentUserUuid)

        val plan = Plan(
            itinStart = request.itinStart,
            itinFinish = request.itinFinish,
            destination = request.location,
            centerLat = request.centerLat,
            centerLng = request.centerLng,
            memo = request.memo,
            owner = user
        )

        planRepository.save(plan)

        val markers = request.markers.map { req ->
            Marker(
                lat = req.lat,
                lng = req.lng,
                date = req.date,
                dayNum = req.dayNum,
                idx = req.idx,
                place = req.place,
                memo = req.memo,
                plan = plan,
            )
        }

        markerRepository.saveAll(markers)
        log.info("New plan created: uuid=${plan.uuid} markers=${markers.size}")

        return CreatePlanResponse.of(plan, markers)
    }

    @Transactional(readOnly = true)
    fun getPlanByUuid(planUuid: UUID): GetPlanResponse {
        val plan = planRepository.findByUuid(planUuid)
            ?: throw PlanNotFoundException(planUuid)
        val currentUserUuid = SecurityUtil.getCurrentUserUuid()

        val canRead = plan.owner.uuid == currentUserUuid
            || postRepository.existsByPlan_UuidAndIsPlanPublicTrue(planUuid)
            || userPartyRepository.existsByParty_Plan_UuidAndUser_UuidAndMemberStatus(
                planUuid, currentUserUuid, MemberStatus.JOINED,
            )
        if (!canRead) {
            throw PlanReadAccessDeniedException(planUuid, currentUserUuid)
        }

        val markers = markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(planUuid)
        return GetPlanResponse.of(plan, markers)
    }

    @Transactional(readOnly = true)
    fun getPlansByUserUuid(userUuid: UUID, pageable: Pageable): Page<GetPlanResponse> {
        if (!userRepository.existsByUuid(userUuid)) {
            throw UserNotFoundException(userUuid)
        }

        return planRepository.findAllByOwner_Uuid(userUuid, pageable)
            .map { GetPlanResponse.of(it, markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(it.uuid)) }
    }

    @Transactional(readOnly = true)
    fun getMyPlans(pageable: Pageable): Page<GetPlanResponse> {
        val currentUserUuid = SecurityUtil.getCurrentUserUuid()

        return planRepository.findAllByOwner_Uuid(currentUserUuid, pageable)
            .map { GetPlanResponse.of(it, markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(it.uuid)) }
    }

    @Transactional
    fun updatePlan(planUuid: UUID, request: UpdatePlanRequest): UpdatePlanResponse {
        val plan = planRepository.findByUuid(planUuid)
            ?: throw PlanNotFoundException(planUuid)
        val currentUserUuid = SecurityUtil.getCurrentUserUuid()

        if (plan.owner.uuid != currentUserUuid) {
            throw PlanUpdateAccessDeniedException(planUuid, currentUserUuid)
        }

        plan.apply {
            itinStart = request.itinStart
            itinFinish = request.itinFinish
            destination = request.location
            centerLat = request.centerLat
            centerLng = request.centerLng
            memo = request.memo
            favorite = request.favorite
        }

        log.info("Plan updated: uuid=$planUuid")
        return UpdatePlanResponse.of(plan)
    }

    @Transactional
    fun deletePlan(planUuid: UUID) {
        val plan = planRepository.findByUuid(planUuid)
            ?: throw PlanNotFoundException(planUuid)
        val currentUserUuid = SecurityUtil.getCurrentUserUuid()

        if (plan.owner.uuid != currentUserUuid) {
            throw PlanUpdateAccessDeniedException(planUuid, currentUserUuid)
        }

        markerRepository.deleteAllByPlan(plan)
        planRepository.delete(plan)
        log.info("Plan deleted: uuid=$planUuid")
    }
}
