package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.plan.dto.CreateMarkerRequest
import com.dogGetDrunk.meetjyou.plan.dto.MarkerResponse
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

    @Transactional(readOnly = true)
    fun getMarkersByPlan(planUuid: UUID): List<MarkerResponse> {
        if (!planRepository.existsByUuid(planUuid)) {
            throw PlanNotFoundException(planUuid)
        }

        return markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(planUuid)
            .map { MarkerResponse.of(it) }
    }

    @Transactional
    fun replaceMarkers(planUuid: UUID, markers: List<CreateMarkerRequest>): List<MarkerResponse> {
        val plan = planRepository.findByUuid(planUuid)
            ?: throw PlanNotFoundException(planUuid)

        val currentUserUuid = SecurityUtil.getCurrentUserUuid()
        if (plan.owner.uuid != currentUserUuid) {
            throw PlanUpdateAccessDeniedException(planUuid, currentUserUuid)
        }

        markerRepository.deleteAllByPlan(plan)

        val newMarkers = markers.map { req ->
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

        markerRepository.saveAll(newMarkers)
        log.info("Markers replaced for plan=$planUuid count=${newMarkers.size}")

        return markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(planUuid)
            .map { MarkerResponse.of(it) }
    }
}
