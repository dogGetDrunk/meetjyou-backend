package com.dogGetDrunk.meetjyou.chat.participant

import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.ChatRoomNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@Service
class ChatParticipantService(
    private val chatParticipantRepository: ChatParticipantRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val userRepository: UserRepository,
    private val userPartyRepository: UserPartyRepository,
) {

    private val log = LoggerFactory.getLogger(ChatParticipantService::class.java)

    @Transactional
    fun enterRoom(
        roomUuid: UUID,
        userUuid: UUID,
    ) {
        validateMembership(roomUuid, userUuid)

        val room = chatRoomRepository.findByUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        val user = userRepository.findByUuid(userUuid)
            ?: throw UserNotFoundException(userUuid)

        val participant = chatParticipantRepository.findByUser_UuidAndRoom_Uuid(userUuid, roomUuid)
            ?: ChatParticipant(
                user = user,
                room = room,
                lastReadAt = null,
            )

        participant.lastReadAt = Instant.now()
        chatParticipantRepository.save(participant)

        log.info("Chat room entry marked as read. roomUuid={}, userUuid={}", roomUuid, userUuid)
    }

    @Transactional
    fun markReadForUsers(
        roomUuid: UUID,
        userUuids: Set<UUID>,
        readAt: Instant,
    ) {
        if (userUuids.isEmpty()) {
            return
        }

        val room = chatRoomRepository.findByUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        val existingParticipants = chatParticipantRepository.findAllByRoom_UuidAndUser_UuidIn(roomUuid, userUuids)
        val existingByUserUuid = existingParticipants.associateBy { it.user.uuid }

        val participantsToSave = userUuids.mapNotNull { userUuid ->
            val existing = existingByUserUuid[userUuid]

            if (existing != null) {
                existing.lastReadAt = readAt
                existing
            } else {
                val user = userRepository.findByUuid(userUuid)
                    ?: return@mapNotNull null

                ChatParticipant(
                    user = user,
                    room = room,
                    lastReadAt = readAt,
                )
            }
        }

        chatParticipantRepository.saveAll(participantsToSave)

        log.info(
            "Chat read timestamp updated for users. roomUuid={}, userCount={}, readAt={}",
            roomUuid,
            participantsToSave.size,
            readAt,
        )
    }

    private fun validateMembership(
        roomUuid: UUID,
        userUuid: UUID,
    ) {
        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        val hasPermission = userPartyRepository.existsByParty_UuidAndUser_UuidAndMemberStatus(
            partyUuid = partyUuid,
            userUuid = userUuid,
            memberStatus = MemberStatus.JOINED,
        )

        if (!hasPermission) {
            log.warn(
                "Chat participant access denied because user does not belong to the party. roomUuid={}, userUuid={}",
                roomUuid,
                userUuid,
            )
            throw IllegalArgumentException("User does not have permission to enter this chat room.")
        }
    }
}
