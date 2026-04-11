package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.MarkerNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.plan.dto.CreateMarkerRequest
import com.dogGetDrunk.meetjyou.plan.dto.MarkerResponse
import com.dogGetDrunk.meetjyou.plan.dto.UpdateMarkerRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MarkerService(
    private val markerRepository: MarkerRepository,
    private val planRepository: PlanRepository,
) {
    private val log = LoggerFactory.getLogger(MarkerService::class.java)

    @Transactional
    fun createMarker(planUuid: UUID, request: CreateMarkerRequest): MarkerResponse {
        val plan = planRepository.findByUuid(planUuid)
            ?: throw PlanNotFoundException(planUuid)
        val currentUserUuid = SecurityUtil.getCurrentUserUuid()

        if (plan.owner.uuid != currentUserUuid) {
            throw PlanUpdateAccessDeniedException(planUuid, currentUserUuid)
        }

        val marker = Marker(
            lat = request.lat,
            lng = request.lng,
            date = request.date,
            dayNum = request.dayNum,
            idx = request.idx,
            place = request.place,
            memo = request.memo,
            plan = plan,
        )

        markerRepository.save(marker)
        log.info("New marker created: uuid=${marker.uuid} for plan=$planUuid")

        return MarkerResponse.of(marker)
    }

    @Transactional(readOnly = true)
    fun getMarkerByUuid(planUuid: UUID, markerUuid: UUID): MarkerResponse {
        val marker = markerRepository.findByUuid(markerUuid)
            ?: throw MarkerNotFoundException(markerUuid)

        if (marker.plan.uuid != planUuid) {
            throw PlanNotFoundException(planUuid)
        }

        return MarkerResponse.of(marker)
    }

    @Transactional(readOnly = true)
    fun getMarkersByPlan(planUuid: UUID): List<MarkerResponse> {
        if (!planRepository.existsByUuid(planUuid)) {
            throw PlanNotFoundException(planUuid)
        }

        return markerRepository.findAllByPlan_Uuid(planUuid)
            .map { MarkerResponse.of(it) }
    }

    @Transactional
    fun updateMarker(planUuid: UUID, markerUuid: UUID, request: UpdateMarkerRequest): MarkerResponse {
        val marker = markerRepository.findByUuid(markerUuid)
            ?: throw MarkerNotFoundException(markerUuid)

        if (marker.plan.uuid != planUuid) {
            throw PlanNotFoundException(planUuid)
        }

        val currentUserUuid = SecurityUtil.getCurrentUserUuid()
        if (marker.plan.owner.uuid != currentUserUuid) {
            throw PlanUpdateAccessDeniedException(planUuid, currentUserUuid)
        }

        marker.apply {
            lat = request.lat
            lng = request.lng
            date = request.date
            dayNum = request.dayNum
            idx = request.idx
            place = request.place
            memo = request.memo
        }

        log.info("Marker updated: uuid=$markerUuid")
        return MarkerResponse.of(marker)
    }

    @Transactional
    fun deleteMarker(planUuid: UUID, markerUuid: UUID) {
        val marker = markerRepository.findByUuid(markerUuid)
            ?: throw MarkerNotFoundException(markerUuid)

        if (marker.plan.uuid != planUuid) {
            throw PlanNotFoundException(planUuid)
        }

        val currentUserUuid = SecurityUtil.getCurrentUserUuid()
        if (marker.plan.owner.uuid != currentUserUuid) {
            throw PlanUpdateAccessDeniedException(planUuid, currentUserUuid)
        }

        markerRepository.delete(marker)
        log.info("Marker deleted: uuid=$markerUuid")
    }
}
