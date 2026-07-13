package com.dogGetDrunk.meetjyou.chat.participant

import com.dogGetDrunk.meetjyou.chat.connection.ChatSessionTracker
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.chat.ChatRoomAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.ChatRoomNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ChatParticipantService(
    private val chatParticipantRepository: ChatParticipantRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val userRepository: UserRepository,
    private val userPartyRepository: UserPartyRepository,
    private val chatSessionTracker: ChatSessionTracker,
) {

    private val log = LoggerFactory.getLogger(ChatParticipantService::class.java)

    @Transactional
    fun enterRoom(
        roomUuid: UUID,
        userUuid: UUID,
    ) {
        validateMembership(roomUuid, userUuid)

        val alreadyParticipant = chatParticipantRepository.findByUser_UuidAndRoom_Uuid(userUuid, roomUuid) != null
        if (alreadyParticipant) {
            return
        }

        val room = chatRoomRepository.findByUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        val user = userRepository.findByUuid(userUuid)
            ?: throw UserNotFoundException(userUuid)

        chatParticipantRepository.save(
            ChatParticipant(
                user = user,
                room = room,
            )
        )

        log.info("Chat room entry tracked. roomUuid={}, userUuid={}", roomUuid, userUuid)
    }

    @Transactional
    fun removeParticipant(
        roomUuid: UUID,
        userUuid: UUID,
    ) {
        chatParticipantRepository.deleteByUser_UuidAndRoom_Uuid(
            userUuid = userUuid,
            roomUuid = roomUuid,
        )
        chatSessionTracker.disconnectAllSessions(roomUuid, userUuid)

        log.info("Chat participant removed. roomUuid={}, userUuid={}", roomUuid, userUuid)
    }

    private fun validateMembership(
        roomUuid: UUID,
        userUuid: UUID,
    ) {
        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        val membership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)

        if (membership?.isActiveMember() != true) {
            log.warn(
                "Chat participant access denied because user does not belong to the party. roomUuid={}, userUuid={}",
                roomUuid,
                userUuid,
            )
            throw ChatRoomAccessDeniedException(userUuid.toString())
        }
    }
}
