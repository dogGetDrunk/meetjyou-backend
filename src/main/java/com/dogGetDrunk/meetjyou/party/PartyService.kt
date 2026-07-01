package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.common.exception.business.party.HostBanNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.HostLeaveNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.InactiveMemberBanException
import com.dogGetDrunk.meetjyou.common.exception.business.party.InactiveMemberLeaveException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyFullException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinAlreadyMemberException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinAlreadyPendingException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinBannedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinRequestNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyRecruitmentClosedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.SelfBanNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinCancelNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinRejectedCooldownException
import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.event.NotificationEvent
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.GetMyPartyResponse
import com.dogGetDrunk.meetjyou.party.dto.GetPartyResponse
import com.dogGetDrunk.meetjyou.party.dto.GetPendingJoinRequestsResponse
import com.dogGetDrunk.meetjyou.party.dto.JoinPartyResponse
import com.dogGetDrunk.meetjyou.party.dto.JoinRequestStatus
import com.dogGetDrunk.meetjyou.party.dto.MyApplicationResponse
import com.dogGetDrunk.meetjyou.party.dto.PendingJoinRequest
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyResponse
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
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
    private val publisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(PartyService::class.java)

    data class PartyCreationResult(val party: Party, val chatRoom: ChatRoom)

    @Transactional
    fun createParty(request: CreatePartyRequest): PartyCreationResult {
        log.info("Party creation request received: name=${request.name}")
        val plan = request.planUuid?.let { planUuid ->
            planRepository.findByUuid(planUuid) ?: throw PlanNotFoundException(planUuid)
        }

        val owner = userRepository.findByUuid(request.ownerUuid)
            ?: throw UserNotFoundException(request.ownerUuid)

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
        userPartyRepository.save(UserParty(party, owner, PartyRole.HOST))

        val chatRoom = ChatRoom(party = party)
        chatRoomRepository.save(chatRoom)

        log.info("Party created: uuid=${party.uuid}, name=${party.name}, roomUuid=${chatRoom.uuid}")

        return PartyCreationResult(party, chatRoom)
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
    fun getMyParties(userUuid: UUID, pageable: Pageable): Page<GetMyPartyResponse> {
        val membershipPage = userPartyRepository.findAllWithPartyByUserUuidAndMemberStatus(userUuid, MemberStatus.JOINED, pageable)
        val partyUuids = membershipPage.content.map { it.party.uuid }
        val roomByPartyUuid = chatRoomRepository.findAllWithPartyByPartyUuidIn(partyUuids)
            .associateBy { it.party.uuid }
        return membershipPage.map { userParty ->
            val chatRoom = roomByPartyUuid[userParty.party.uuid]
                ?: error("ChatRoom not found for party ${userParty.party.uuid}")
            GetMyPartyResponse.of(userParty, chatRoom)
        }
    }

    @Transactional
    fun requestJoinParty(partyUuid: UUID, userUuid: UUID, applicationNote: String?): JoinPartyResponse {
        log.info("Party join requested. partyUuid={}, userUuid={}", partyUuid, userUuid)

        val party = requireParty(partyUuid)

        if (party.recruitmentStatus != PartyRecruitmentStatus.OPEN) {
            throw PartyRecruitmentClosedException(partyUuid)
        }

        if (party.joined >= party.capacity) {
            throw PartyFullException(partyUuid)
        }

        val existing = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)
        when (existing?.memberStatus) {
            MemberStatus.PENDING  -> throw PartyJoinAlreadyPendingException(partyUuid, userUuid)
            MemberStatus.JOINED   -> throw PartyJoinAlreadyMemberException(partyUuid, userUuid)
            MemberStatus.BANNED   -> throw PartyJoinBannedException(partyUuid, userUuid)
            MemberStatus.LEFT     -> throw PartyJoinNotAllowedException(partyUuid, userUuid)
            MemberStatus.REJECTED -> {
                if (existing.statusChangedAt.isAfter(Instant.now().minus(24, ChronoUnit.HOURS))) {
                    throw PartyJoinRejectedCooldownException(partyUuid, userUuid)
                }
                existing.applicationNote = applicationNote
                existing.pending()
            }
            null -> {
                val user = userRepository.findByUuid(userUuid) ?: throw UserNotFoundException(userUuid)
                userPartyRepository.save(
                    UserParty(party, user, PartyRole.MEMBER).also {
                        it.applicationNote = applicationNote
                        it.pending()
                    }
                )
            }
        }

        userPartyRepository.findByParty_UuidAndRole(partyUuid, PartyRole.HOST)?.let { host ->
            val applicant = userRepository.findByUuid(userUuid) ?: throw UserNotFoundException(userUuid)
            publisher.publishEvent(
                NotificationEvent(
                    userUuid = host.user.uuid,
                    payload = NotificationPayload(
                        type = NotificationType.PARTY_JOIN_REQUEST,
                        bodyArgs = mapOf("applicant" to applicant.nickname),
                        data = mapOf(
                            "partyUuid" to partyUuid.toString(),
                            "partyName" to party.name,
                            "applicantUuid" to userUuid.toString(),
                            "applicantNickname" to applicant.nickname,
                            "requestedAt" to Instant.now().toString(),
                        ),
                        dedupKey = "join_request:${partyUuid}:${userUuid}",
                    ),
                )
            )
        }

        log.info("Party join request submitted. partyUuid={}, userUuid={}", partyUuid, userUuid)
        return JoinPartyResponse(partyUuid = partyUuid, status = "PENDING")
    }

    @Transactional
    fun cancelJoinRequest(partyUuid: UUID, userUuid: UUID) {
        log.info("Party join cancellation requested. partyUuid={}, userUuid={}", partyUuid, userUuid)
        val membership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)
            ?: throw PartyJoinRequestNotFoundException(partyUuid, userUuid)
        if (membership.memberStatus != MemberStatus.PENDING) {
            throw PartyJoinCancelNotAllowedException(partyUuid, userUuid)
        }
        userPartyRepository.deleteByParty_UuidAndUser_Uuid(partyUuid, userUuid)
        log.info("Party join cancelled. partyUuid={}, userUuid={}", partyUuid, userUuid)
    }

    @Transactional
    fun approveJoinRequest(partyUuid: UUID, hostUuid: UUID, applicantUuid: UUID) {
        log.info("Join request approval requested. partyUuid={}, hostUuid={}, applicantUuid={}", partyUuid, hostUuid, applicantUuid)

        requireActiveHostMembership(partyUuid, hostUuid)

        val party = partyRepository.findByUuidForUpdate(partyUuid) ?: throw PartyNotFoundException(partyUuid)
        if (party.joined >= party.capacity) {
            throw PartyFullException(partyUuid)
        }

        val request = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, applicantUuid)
            ?.takeIf { it.memberStatus == MemberStatus.PENDING }
            ?: throw PartyJoinRequestNotFoundException(partyUuid, applicantUuid)

        request.approve()
        party.joined++

        chatRoomRepository.findByParty_Uuid(partyUuid)?.let { chatRoom ->
            chatParticipantService.enterRoom(chatRoom.uuid, applicantUuid)
        }

        publisher.publishEvent(
            NotificationEvent(
                userUuid = applicantUuid,
                payload = NotificationPayload(
                    type = NotificationType.PARTY_JOIN_ACCEPTED,
                    bodyArgs = mapOf("partyName" to party.name),
                    data = mapOf(
                        "type" to "PARTY_JOIN_ACCEPTED",
                        "partyUuid" to partyUuid.toString(),
                        "partyName" to party.name,
                    ),
                    dedupKey = "join_accepted:${partyUuid}:${applicantUuid}",
                ),
            )
        )

        val applicant = userRepository.findByUuid(applicantUuid) ?: throw UserNotFoundException(applicantUuid)
        userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(partyUuid, MemberStatus.JOINED)
            .filter { it.user.uuid != applicantUuid }
            .forEach { membership ->
                publisher.publishEvent(
                    NotificationEvent(
                        userUuid = membership.user.uuid,
                        payload = NotificationPayload(
                            type = NotificationType.PARTY_MEMBER_JOINED,
                            bodyArgs = mapOf("member" to applicant.nickname),
                            data = mapOf("partyUuid" to partyUuid.toString()),
                        ),
                    )
                )
            }

        log.info("Join request approved. partyUuid={}, applicantUuid={}", partyUuid, applicantUuid)
    }

    @Transactional
    fun rejectJoinRequest(partyUuid: UUID, hostUuid: UUID, applicantUuid: UUID) {
        log.info("Join request rejection requested. partyUuid={}, hostUuid={}, applicantUuid={}", partyUuid, hostUuid, applicantUuid)

        requireActiveHostMembership(partyUuid, hostUuid)

        val request = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, applicantUuid)
            ?.takeIf { it.memberStatus == MemberStatus.PENDING }
            ?: throw PartyJoinRequestNotFoundException(partyUuid, applicantUuid)

        request.reject()

        publisher.publishEvent(
            NotificationEvent(
                userUuid = applicantUuid,
                payload = NotificationPayload(
                    type = NotificationType.PARTY_JOIN_REJECTED,
                    bodyArgs = mapOf("partyName" to request.party.name),
                    data = mapOf(
                        "type" to "PARTY_JOIN_REJECTED",
                        "partyUuid" to partyUuid.toString(),
                        "partyName" to request.party.name,
                    ),
                    dedupKey = "join_rejected:${partyUuid}:${applicantUuid}",
                ),
            )
        )

        log.info("Join request rejected. partyUuid={}, applicantUuid={}", partyUuid, applicantUuid)
    }

    @Transactional(readOnly = true)
    fun getPendingJoinRequests(partyUuid: UUID, hostUuid: UUID): GetPendingJoinRequestsResponse {
        requireActiveHostMembership(partyUuid, hostUuid)

        val pending = userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(partyUuid, MemberStatus.PENDING)
        val postUuid = postRepository.findByParty_Uuid(partyUuid)?.uuid

        return GetPendingJoinRequestsResponse(
            partyUuid = partyUuid,
            postUuid = postUuid,
            requests = pending.map { up ->
                PendingJoinRequest(
                    userUuid = up.user.uuid,
                    nickname = up.user.nickname,
                    thumbImgUrl = up.user.resolveThumbImgUrl(),
                    applicationNote = up.applicationNote,
                    requestedAt = up.joinedAt,
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun getMyApplications(userUuid: UUID, pageable: Pageable): Page<MyApplicationResponse> {
        val applicationPage = userPartyRepository.findAllSentApplicationsByUserUuid(userUuid, pageable)
        val partyUuids = applicationPage.content.map { it.party.uuid }
        val postByPartyUuid = postRepository.findAllByParty_UuidIn(partyUuids).associateBy { it.party.uuid }
        return applicationPage.map { up ->
            val status = when (up.memberStatus) {
                MemberStatus.JOINED   -> JoinRequestStatus.ACCEPTED
                MemberStatus.REJECTED -> JoinRequestStatus.REJECTED
                else                  -> JoinRequestStatus.PENDING
            }
            MyApplicationResponse(
                partyUuid = up.party.uuid,
                partyName = up.party.name,
                postUuid = postByPartyUuid[up.party.uuid]?.uuid,
                status = status,
                applicationNote = up.applicationNote,
                appliedAt = up.joinedAt,
                statusChangedAt = up.statusChangedAt,
            )
        }
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

        party.apply {
            name = request.name
            destination = request.destination
            joined = request.joined
            capacity = request.capacity
            itinStart = request.itinStart
            itinFinish = request.itinFinish
        }
        log.info("Party is updated: uuid=$partyUuid")
        return UpdatePartyResponse.of(party)
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
            throw SelfBanNotAllowedException(userUuid)
        }

        val targetMembership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, targetUserUuid)
            ?: throw UserNotFoundException(targetUserUuid)

        if (targetMembership.role == PartyRole.HOST) {
            throw HostBanNotAllowedException(targetUserUuid)
        }

        if (!targetMembership.isActiveMember()) {
            throw InactiveMemberBanException(targetUserUuid)
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
            throw HostLeaveNotAllowedException(partyUuid, userUuid)
        }

        if (!membership.isActiveMember()) {
            throw InactiveMemberLeaveException(userUuid)
        }

        membership.leave()
        party.joined--
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
