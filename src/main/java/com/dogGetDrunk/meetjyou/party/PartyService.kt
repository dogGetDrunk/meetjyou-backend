package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PartyNotFoundException
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyResponse
import com.dogGetDrunk.meetjyou.party.dto.GetPartyResponse
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyResponse
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val planRepository: PlanRepository,
    private val userPartyRepository: UserPartyRepository
) {
    private val log = LoggerFactory.getLogger(PartyService::class.java)

    @Transactional
    fun createParty(request: CreatePartyRequest): CreatePartyResponse {
        val plan = planRepository.findByUuid(request.planUuid)
            ?: throw IllegalArgumentException("해당 Plan이 존재하지 않습니다.")

        val party = Party(
            itinStart = request.itinStart,
            itinFinish = request.itinFinish,
            destination = request.destination,
            joined = request.joined,
            max = request.max,
            name = request.name,
            imgUrl = request.imgUrl,
            thumbImgUrl = request.thumbImgUrl,
        ).apply {
            this.plan = plan
        }
        partyRepository.save(party)
        log.info("Party created: uuid=${'$'}{party.uuid}")

        return CreatePartyResponse.of(party)
    }

    @Transactional(readOnly = true)
    fun getPartyByUuid(uuid: UUID): GetPartyResponse {
        val party = partyRepository.findByUuid(uuid)
            ?: throw PartyNotFoundException(uuid)

        return GetPartyResponse.of(party)
    }

    @Transactional(readOnly = true)
    fun getAllParties(pageable: Pageable): Page<GetPartyResponse> {
        return partyRepository.findAll(pageable).map { GetPartyResponse.of(it) }
    }

    @Transactional(readOnly = true)
    fun getPartiesByPlanUuid(planUuid: UUID, pageable: Pageable): Page<GetPartyResponse> {
        return partyRepository.findAllByPlan_Uuid(planUuid, pageable).map { GetPartyResponse.of(it) }
    }

    @Transactional(readOnly = true)
    fun getPartiesByUserUuid(userUuid: UUID, pageable: Pageable): Page<GetPartyResponse> {
        return userPartyRepository.findAllByUser_Uuid(userUuid, pageable)
            .map { GetPartyResponse.of(it.party) }
    }

    @Transactional
    fun updateParty(uuid: UUID, request: UpdatePartyRequest): UpdatePartyResponse {
        val party = partyRepository.findByUuid(uuid)
            ?: throw PartyNotFoundException(uuid)

        request.name?.let { party.name = it }
        request.destination?.let { party.destination = it }
        request.joined?.let { party.joined = it }
        request.max?.let { party.max = it }
        request.itinStart?.let { party.itinStart = it }
        request.itinFinish?.let { party.itinFinish = it }
        request.imgUrl?.let { party.imgUrl = it }
        request.thumbImgUrl?.let { party.thumbImgUrl = it }

        log.info("Party updated: uuid=${'$'}uuid")
        return UpdatePartyResponse.of(party)
    }

    @Transactional
    fun deleteParty(uuid: UUID) {
        val party = partyRepository.findByUuid(uuid)
            ?: throw PartyNotFoundException(uuid)

        partyRepository.delete(party)
        log.info("Party deleted: uuid=${'$'}uuid")
    }
}
