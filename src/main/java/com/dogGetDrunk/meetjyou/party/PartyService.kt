package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PartyNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.image.ImageService
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyResponse
import com.dogGetDrunk.meetjyou.party.dto.GetPartyResponse
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyResponse
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
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
    private val userPartyRepository: UserPartyRepository,
    private val imageService: ImageService,
    private val userRepository: UserRepository,
) {
    private val log = LoggerFactory.getLogger(PartyService::class.java)

    @Transactional
    fun createParty(request: CreatePartyRequest): CreatePartyResponse {
        val plan = request.planUuid?.let { planUuid ->
            planRepository.findByUuid(planUuid)
                ?: throw PlanNotFoundException(planUuid)
        }

        val owner = userRepository.findByUuid(request.ownerUuid)
            ?: throw UserNotFoundException(request.ownerUuid)

        val party = Party(
            itinStart = request.itinStart,
            itinFinish = request.itinFinish,
            location = request.destination,
            joined = request.joined,
            capacity = request.capacity,
            name = request.name,
        ).apply {
            this.plan = plan
            this.owner = owner
        }

        partyRepository.save(party)
        userPartyRepository.save(UserParty(party, owner, PartyRole.LEADER))

        log.info("Party created: uuid=${party.uuid}")

        imageService.setDefaultPartyImage(party.uuid, request.postUuid)

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

    @Transactional(readOnly = true)
    fun verifyPartyOwner(partyUuid: UUID, userUuid: UUID): Boolean {
        return partyRepository.existsByUuidAndOwner_Uuid(partyUuid, userUuid)
    }

    @Transactional
    fun updateParty(uuid: UUID, request: UpdatePartyRequest): UpdatePartyResponse {
        val party = partyRepository.findByUuid(uuid)
            ?: throw PartyNotFoundException(uuid)

        return party.apply {
            name = request.name
            location = request.location
            joined = request.joined
            capacity = request.capacity
            itinStart = request.itinStart
            itinFinish = request.itinFinish
        }.also {
            log.info("Party updated: uuid=$uuid")
        }.let {
            UpdatePartyResponse.of(it)
        }
    }

    @Transactional
    fun deleteParty(uuid: UUID) {
        val party = partyRepository.findByUuid(uuid)
            ?: throw PartyNotFoundException(uuid)

        partyRepository.delete(party)
        log.info("Party deleted: uuid=${'$'}uuid")
    }
}
