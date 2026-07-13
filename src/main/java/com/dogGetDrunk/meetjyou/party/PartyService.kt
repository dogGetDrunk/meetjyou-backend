package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.common.exception.business.party.HostBanNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.HostLeaveNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.InactiveMemberBanException
import com.dogGetDrunk.meetjyou.common.exception.business.party.InactiveMemberLeaveException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyCapacityBelowJoinedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyFullException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinAlreadyMemberException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinAlreadyPendingException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinBannedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinRequestNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyMemberAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyRecruitmentClosedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.SelfBanNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.ChatRoomNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinCancelNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinRejectedCooldownException
import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
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
import com.dogGetDrunk.meetjyou.party.dto.PartyMemberResponse
import com.dogGetDrunk.meetjyou.party.dto.PendingJoinRequest
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyResponse
import com.dogGetDrunk.meetjyou.plan.Marker
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.dto.GetPlanResponse
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val markerRepository: MarkerRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatParticipantService: ChatParticipantService,
    private val chatRoomEventBroadcaster: ChatRoomEventBroadcaster,
    private val userPartyRepository: UserPartyRepository,
    private val userRepository: UserRepository,
    private val publisher: ApplicationEventPublisher,
    private val partyImgService: PartyImgService,
    private val postImgService: PostImgService,
    private val objectMapper: ObjectMapper,
    private val currentUserProvider: CurrentUserProvider,
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

        return GetPartyResponse.of(party, deserializePlanSnapshot(party))
    }

    @Transactional(readOnly = true)
    fun getAllParties(pageable: Pageable): Page<GetPartyResponse> {
        return partyRepository.findAllWithPlan(pageable).map { GetPartyResponse.of(it) }
    }

    @Transactional(readOnly = true)
    fun getPartiesByPlanUuid(planUuid: UUID, pageable: Pageable): Page<GetPartyResponse> {
        return partyRepository.findAllByPlanUuidWithPlan(planUuid, pageable).map { GetPartyResponse.of(it) }
    }

    @Transactional(readOnly = true)
    fun getPartiesByUserUuid(userUuid: UUID, pageable: Pageable): Page<GetPartyResponse> {
        return userPartyRepository.findAllWithPartyByUserUuid(userUuid, pageable).map { GetPartyResponse.of(it.party) }
    }

    @Transactional(readOnly = true)
    fun getMyParties(pageable: Pageable): Page<GetMyPartyResponse> {
        val userUuid = currentUserProvider.uuid
        val membershipPage = userPartyRepository.findAllWithPartyByUserUuidAndMemberStatus(userUuid, MemberStatus.JOINED, pageable)
        val partyUuids = membershipPage.content.map { it.party.uuid }
        val roomByPartyUuid = chatRoomRepository.findAllWithPartyByPartyUuidIn(partyUuids)
            .associateBy { it.party.uuid }
        return membershipPage.map { userParty ->
            val chatRoom = roomByPartyUuid[userParty.party.uuid]
                ?: throw ChatRoomNotFoundException(userParty.party.uuid.toString())
            GetMyPartyResponse.of(userParty, chatRoom)
        }
    }

    @Transactional
    fun requestJoinParty(partyUuid: UUID, applicationNote: String?): JoinPartyResponse {
        val userUuid = currentUserProvider.uuid
        log.info("Party join requested. partyUuid={}, userUuid={}", partyUuid, userUuid)

        val party = requireParty(partyUuid)

        if (party.recruitmentStatus != PartyRecruitmentStatus.OPEN) {
            throw PartyRecruitmentClosedException(partyUuid)
        }
        if (party.joined >= party.capacity) {
            throw PartyFullException(partyUuid)
        }

        val existing = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)
        applyJoinRequestState(party, userUuid, applicationNote, existing)
        notifyHostOfJoinRequest(party, partyUuid, userUuid)

        log.info("Party join request submitted. partyUuid={}, userUuid={}", partyUuid, userUuid)
        return JoinPartyResponse(partyUuid = partyUuid, status = "PENDING")
    }

    private fun applyJoinRequestState(party: Party, userUuid: UUID, applicationNote: String?, existing: UserParty?) {
        val partyUuid = party.uuid
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
    }

    private fun notifyHostOfJoinRequest(party: Party, partyUuid: UUID, userUuid: UUID) {
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
    }

    @Transactional
    fun cancelJoinRequest(partyUuid: UUID) {
        val userUuid = currentUserProvider.uuid
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
    fun approveJoinRequest(partyUuid: UUID, applicantUuid: UUID) {
        val hostUuid = currentUserProvider.uuid
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
            chatRoomEventBroadcaster.broadcastMemberJoined(
                roomUuid = chatRoom.uuid,
                partyUuid = partyUuid,
                targetUserUuid = applicantUuid,
            )
        }

        notifyApplicantApproved(party, partyUuid, applicantUuid)
        notifyExistingMembersOfNewMember(partyUuid, applicantUuid)

        log.info("Join request approved. partyUuid={}, applicantUuid={}", partyUuid, applicantUuid)
    }

    private fun notifyApplicantApproved(party: Party, partyUuid: UUID, applicantUuid: UUID) {
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
    }

    private fun notifyExistingMembersOfNewMember(partyUuid: UUID, applicantUuid: UUID) {
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
    }

    @Transactional
    fun rejectJoinRequest(partyUuid: UUID, applicantUuid: UUID) {
        val hostUuid = currentUserProvider.uuid
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
    fun getPendingJoinRequests(partyUuid: UUID): GetPendingJoinRequestsResponse {
        requireActiveHostMembership(partyUuid, currentUserProvider.uuid)

        val pending = userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(partyUuid, MemberStatus.PENDING)
        val postUuid = postRepository.findByParty_Uuid(partyUuid)?.uuid

        return GetPendingJoinRequestsResponse(
            partyUuid = partyUuid,
            postUuid = postUuid,
            requests = pending.map { up ->
                PendingJoinRequest(
                    userUuid = up.user.uuid,
                    nickname = up.user.nickname,
                    hasProfileImage = up.user.hasProfileImage,
                    applicationNote = up.applicationNote,
                    requestedAt = up.joinedAt,
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun getPartyMembers(partyUuid: UUID): List<PartyMemberResponse> {
        val userUuid = currentUserProvider.uuid
        val membership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)
        if (membership == null || !membership.isActiveMember()) {
            throw PartyMemberAccessDeniedException(partyUuid, userUuid)
        }

        return userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(partyUuid, MemberStatus.JOINED)
            .sortedBy { it.joinedAt }
            .map { PartyMemberResponse.of(it) }
    }

    @Transactional(readOnly = true)
    fun getMyApplications(pageable: Pageable): Page<MyApplicationResponse> {
        val applicationPage = userPartyRepository.findAllSentApplicationsByUserUuid(currentUserProvider.uuid, pageable)
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

    @Transactional(readOnly = true)
    fun assertCurrentUserIsHost(partyUuid: UUID) {
        val userUuid = currentUserProvider.uuid
        if (!verifyPartyHost(partyUuid, userUuid)) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }
    }

    @Transactional
    fun updateParty(partyUuid: UUID, request: UpdatePartyRequest): UpdatePartyResponse {
        val userUuid = currentUserProvider.uuid
        log.info("Party update request received: uuid=$partyUuid by user=$userUuid")
        if (!verifyPartyHost(partyUuid, userUuid)) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }

        val party = partyRepository.findByUuidForUpdate(partyUuid) ?: throw PartyNotFoundException(partyUuid)
        validatePartyWritable(party)
        validateCapacityChange(party, request.capacity)

        party.apply {
            name = request.name
            destination = request.destination
            capacity = request.capacity
            itinStart = request.itinStart
            itinFinish = request.itinFinish
        }
        applyPlanChange(party, request, userUuid)
        log.info("Party is updated: uuid=$partyUuid")
        return UpdatePartyResponse.of(party)
    }

    private fun validateCapacityChange(party: Party, newCapacity: Int) {
        if (newCapacity < party.joined) {
            throw PartyCapacityBelowJoinedException(party.uuid)
        }
    }

    private fun applyPlanChange(party: Party, request: UpdatePartyRequest, userUuid: UUID) {
        party.plan = request.planUuid?.let { resolveOwnedPlan(it, userUuid) }
        syncPostPlan(party)
    }

    private fun syncPostPlan(party: Party) {
        val post = postRepository.findByParty_Uuid(party.uuid) ?: return
        post.plan = party.plan
        post.isPlanPublic = if (party.plan == null) null else (post.isPlanPublic ?: false)
        log.info("Post plan synced with party. partyUuid=${party.uuid}, planUuid=${party.plan?.uuid}")
    }

    @Transactional
    fun deleteParty(partyUuid: UUID) {
        val userUuid = currentUserProvider.uuid
        log.info("Party deletion request received: uuid=$partyUuid")
        if (!verifyPartyHost(partyUuid, userUuid)) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }

        val party = partyRepository.findByUuid(partyUuid) ?: throw PartyNotFoundException(partyUuid)
        validatePartyWritable(party)

        userPartyRepository.deleteAllByParty_Uuid(partyUuid)
        partyRepository.delete(party)
        log.info("Party is deleted: uuid=$partyUuid")
    }

    @Transactional
    fun completeParty(partyUuid: UUID) {
        val userUuid = currentUserProvider.uuid
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
        snapshotPlan(party)
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
        targetUserUuid: UUID,
    ) {
        val userUuid = currentUserProvider.uuid
        log.info(
            "Party member ban requested. partyUuid={}, userUuid={}, targetUserUuid={}",
            partyUuid,
            userUuid,
            targetUserUuid,
        )

        val party = partyRepository.findByUuidForUpdate(partyUuid) ?: throw PartyNotFoundException(partyUuid)
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
        party.joined--
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
    ) {
        val userUuid = currentUserProvider.uuid
        log.info("Party leave requested. partyUuid={}, userUuid={}", partyUuid, userUuid)

        val party = partyRepository.findByUuidForUpdate(partyUuid) ?: throw PartyNotFoundException(partyUuid)
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

    @Transactional
    fun confirmPartyImage(partyUuid: UUID) {
        val userUuid = currentUserProvider.uuid
        if (!verifyPartyHost(partyUuid, userUuid)) {
            throw PartyUpdateAccessDeniedException(partyUuid, userUuid)
        }

        requireParty(partyUuid).imageState = PartyImageState.CUSTOM
        log.info("Party image confirmed: uuid=$partyUuid")
    }

    @Transactional
    fun clearPartyImageState(partyUuid: UUID) {
        requireParty(partyUuid).imageState = PartyImageState.NONE
        log.info("Party image cleared: uuid=$partyUuid")
    }

    @Transactional(readOnly = true)
    fun resolvePartyOriginalImageDownload(partyUuid: UUID): ParResponse? {
        val party = requireParty(partyUuid)
        return when (party.imageState) {
            PartyImageState.CUSTOM -> partyImgService.createPartyOriginalImgDownloadPars(party.uuid)
            PartyImageState.NONE -> null
            PartyImageState.INHERITED -> postRepository.findByParty_Uuid(partyUuid)
                ?.let { postImgService.createPostOriginalImgDownloadPars(it.uuid) }
        }
    }

    // Deliberately NOT @Transactional: the OCI PAR calls below are synchronous network I/O, and
    // must not run while holding a pooled DB connection (see ADR — Hikari pool exhaustion).
    // The two repository calls each run in their own short-lived, auto-committed transaction.
    fun resolvePartyThumbnailImageDownloads(partyUuids: List<UUID>): List<ParResponse?> {
        val partyByUuid = partyRepository.findAllByUuidIn(partyUuids).associateBy { it.uuid }
        val postByPartyUuid = postRepository.findAllByParty_UuidIn(partyUuids).associateBy { it.party.uuid }
        return partyUuids.map { resolveThumbnail(partyByUuid[it], postByPartyUuid[it]) }
    }

    private fun resolveThumbnail(party: Party?, post: Post?): ParResponse? {
        if (party == null) return null
        return when (party.imageState) {
            PartyImageState.CUSTOM -> partyImgService.createPartyThumbnailImgDownloadPars(listOf(party.uuid)).firstOrNull()
            PartyImageState.NONE -> null
            PartyImageState.INHERITED -> post?.let { postImgService.createPostThumbnailImgDownloadPars(listOf(it.uuid)).firstOrNull() }
        }
    }

    private fun resolveOwnedPlan(planUuid: UUID, userUuid: UUID): Plan {
        val plan = planRepository.findByUuid(planUuid) ?: throw PlanNotFoundException(planUuid)
        if (plan.owner.uuid != userUuid) {
            throw PlanUpdateAccessDeniedException(planUuid, userUuid)
        }
        return plan
    }

    private fun snapshotPlan(party: Party) {
        val plan = party.plan ?: return
        val markers: List<Marker> = markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(plan.uuid)
        party.planSnapshot = objectMapper.writeValueAsString(GetPlanResponse.of(plan, markers))
    }

    private fun deserializePlanSnapshot(party: Party): GetPlanResponse? {
        val snapshot = party.planSnapshot ?: return null
        return runCatching { objectMapper.readValue(snapshot, GetPlanResponse::class.java) }
            .onFailure { log.warn("Failed to deserialize plan snapshot. partyUuid={}", party.uuid, it) }
            .getOrNull()
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
