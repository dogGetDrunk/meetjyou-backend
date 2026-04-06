package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PostNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyResponse
import com.dogGetDrunk.meetjyou.party.dto.GetPartyResponse
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyResponse
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
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
    private val postRepository: PostRepository,
    private val planRepository: PlanRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatParticipantService: ChatParticipantService,
    private val chatRoomEventBroadcaster: ChatRoomEventBroadcaster,
    private val userPartyRepository: UserPartyRepository,
    private val userRepository: UserRepository,
) {
    private val log = LoggerFactory.getLogger(PartyService::class.java)

    @Transactional
    fun createParty(request: CreatePartyRequest): CreatePartyResponse {
        log.info("Party creation request received: name=${request.name}")
        val plan = request.planUuid?.let { planUuid ->
            planRepository.findByUuid(planUuid) ?: throw PlanNotFoundException(planUuid)
        }

        val owner = userRepository.findByUuid(request.ownerUuid)
            ?: throw UserNotFoundException(request.ownerUuid)
        val post = postRepository.findByUuid(request.postUuid)
            ?: throw PostNotFoundException(request.postUuid)

        val party = Party(
            itinStart = request.itinStart,
            itinFinish = request.itinFinish,
            destination = request.destination,
            joined = request.joined,
            capacity = request.capacity,
            name = request.name,
        ).apply {
            this.plan = plan
        }

        partyRepository.save(party)
        post.party = party
        userPartyRepository.save(UserParty(party, owner, PartyRole.HOST))

        log.info("Party created: uuid=${party.uuid}, name=${party.name}")

        return CreatePartyResponse.of(party)
    }

    @Transactional(readOnly = true)
    fun getPartyByUuid(uuid: UUID): GetPartyResponse {
        val party = partyRepository.findByUuid(uuid) ?: throw PartyNotFoundException(uuid)

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
        return userPartyRepository.findAllByUser_Uuid(userUuid, pageable).map { GetPartyResponse.of(it.party) }
    }

    @Transactional(readOnly = true)
    fun verifyPartyHost(partyUuid: UUID, userUuid: UUID): Boolean {
        val party = partyRepository.findByUuid(partyUuid) ?: return false
        if (party.progressStatus == PartyProgressStatus.COMPLETED) {
            return false
        }

        val userParty = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)
        return userParty?.role == PartyRole.HOST && userParty.isActiveMember()
    }

    @Transactional
    fun updateParty(partyUuid: UUID, userUuid: UUID, request: UpdatePartyRequest): UpdatePartyResponse {
        log.info("Party update request received: uuid=$partyUuid by user=$userUuid")
        if (!verifyPartyHost(partyUuid, userUuid)) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }

        val party = partyRepository.findByUuid(partyUuid) ?: throw PartyNotFoundException(partyUuid)
        validatePartyWritable(party)

        return party.apply {
            name = request.name
            destination = request.destination
            joined = request.joined
            capacity = request.capacity
            itinStart = request.itinStart
            itinFinish = request.itinFinish
        }.also {
            log.info("Party is updated: uuid=$partyUuid")
        }.let {
            UpdatePartyResponse.of(it)
        }
    }

    @Transactional
    fun deleteParty(partyUuid: UUID, userUuid: UUID) {
        log.info("Party deletion request received: uuid=$partyUuid")
        if (!verifyPartyHost(partyUuid, userUuid)) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }

        val party = partyRepository.findByUuid(partyUuid) ?: throw PartyNotFoundException(partyUuid)
        validatePartyWritable(party)

        partyRepository.delete(party)
        userPartyRepository.deleteAllByParty_Uuid(partyUuid)
        log.info("Party is deleted: uuid=$partyUuid")
    }

    @Transactional
    fun completeParty(partyUuid: UUID, userUuid: UUID) {
        log.info("Party completion requested. partyUuid={}, userUuid={}", partyUuid, userUuid)

        val party = requireParty(partyUuid)
        val hostMembership = requireActiveHostMembership(partyUuid, userUuid)

        if (!hostMembership.isActiveMember()) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }

        if (party.progressStatus == PartyProgressStatus.COMPLETED) {
            return
        }

        party.complete()
        postRepository.findByParty_Uuid(partyUuid)?.completeRecruitment()
        chatRoomRepository.findByParty_Uuid(partyUuid)?.let { chatRoom ->
            chatRoomEventBroadcaster.broadcastPartyCompleted(
                roomUuid = chatRoom.uuid,
                partyUuid = partyUuid,
                actorUserUuid = userUuid,
            )
        }

        log.info("Party completed. partyUuid={}, userUuid={}", partyUuid, userUuid)
    }

    @Transactional
    fun banMember(
        partyUuid: UUID,
        userUuid: UUID,
        targetUserUuid: UUID,
    ) {
        log.info(
            "Party member ban requested. partyUuid={}, userUuid={}, targetUserUuid={}",
            partyUuid,
            userUuid,
            targetUserUuid,
        )

        val party = requireParty(partyUuid)
        validatePartyWritable(party)
        requireActiveHostMembership(partyUuid, userUuid)

        if (userUuid == targetUserUuid) {
            throw InvalidInputException(
                value = targetUserUuid.toString(),
                message = "Host cannot ban themselves.",
            )
        }

        val targetMembership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, targetUserUuid)
            ?: throw UserNotFoundException(targetUserUuid)

        if (targetMembership.role == PartyRole.HOST) {
            throw InvalidInputException(
                value = targetUserUuid.toString(),
                message = "Host cannot be banned.",
            )
        }

        if (!targetMembership.isActiveMember()) {
            throw InvalidInputException(
                value = targetUserUuid.toString(),
                message = "Only active members can be banned.",
            )
        }

        targetMembership.ban()
        removeFromChatRoom(partyUuid, targetUserUuid)
        chatRoomRepository.findByParty_Uuid(partyUuid)?.let { chatRoom ->
            chatRoomEventBroadcaster.broadcastMemberBanned(
                roomUuid = chatRoom.uuid,
                partyUuid = partyUuid,
                actorUserUuid = userUuid,
                targetUserUuid = targetUserUuid,
            )
        }

        log.info(
            "Party member banned. partyUuid={}, userUuid={}, targetUserUuid={}",
            partyUuid,
            userUuid,
            targetUserUuid,
        )
    }

    @Transactional
    fun leaveParty(
        partyUuid: UUID,
        userUuid: UUID,
    ) {
        log.info("Party leave requested. partyUuid={}, userUuid={}", partyUuid, userUuid)

        val party = requireParty(partyUuid)
        validatePartyWritable(party)

        val membership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)
            ?: throw PartyUpdateAccessDeniedException(partyUuid, userUuid)

        if (membership.role == PartyRole.HOST) {
            throw InvalidInputException(
                value = userUuid.toString(),
                message = "Host cannot leave a party and must complete it instead.",
            )
        }

        if (!membership.isActiveMember()) {
            throw InvalidInputException(
                value = userUuid.toString(),
                message = "Only active members can leave the party.",
            )
        }

        membership.leave()
        removeFromChatRoom(partyUuid, userUuid)
        chatRoomRepository.findByParty_Uuid(partyUuid)?.let { chatRoom ->
            chatRoomEventBroadcaster.broadcastMemberLeft(
                roomUuid = chatRoom.uuid,
                partyUuid = partyUuid,
                targetUserUuid = userUuid,
            )
        }

        log.info("Party leave completed. partyUuid={}, userUuid={}", partyUuid, userUuid)
    }

    private fun requireParty(partyUuid: UUID): Party {
        return partyRepository.findByUuid(partyUuid) ?: throw PartyNotFoundException(partyUuid)
    }

    private fun requireActiveHostMembership(
        partyUuid: UUID,
        userUuid: UUID,
    ): UserParty {
        val membership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)
            ?: throw PartyUpdateAccessDeniedException(partyUuid, userUuid)

        if (membership.role != PartyRole.HOST || !membership.isActiveMember()) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }

        return membership
    }

    private fun validatePartyWritable(party: Party) {
        if (party.progressStatus == PartyProgressStatus.COMPLETED) {
            throw InvalidInputException(
                value = party.uuid.toString(),
                message = "Completed party is read-only.",
            )
        }
    }

    private fun removeFromChatRoom(
        partyUuid: UUID,
        targetUserUuid: UUID,
    ) {
        val chatRoom = chatRoomRepository.findByParty_Uuid(partyUuid) ?: return
        chatParticipantService.removeParticipant(chatRoom.uuid, targetUserUuid)
    }
}
