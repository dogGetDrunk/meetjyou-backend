package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.dto.ChatRoomSummaryResponse
import com.dogGetDrunk.meetjyou.chat.dto.GetChatMessagesResponse
import com.dogGetDrunk.meetjyou.chat.dto.GetChatRoomsResponse
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageResponse
import com.dogGetDrunk.meetjyou.chat.message.RoomUnreadCountProjection
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantRepository
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.chat.ChatMessageNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.chat.ChatRoomAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.ChatRoomNotFoundException
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ChatReadService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val userPartyRepository: UserPartyRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
) {

    private val log = LoggerFactory.getLogger(ChatReadService::class.java)

    @Transactional(readOnly = true)
    fun getMessages(
        roomUuid: UUID,
        requesterUuid: UUID,
        beforeMessageUuid: UUID?,
        size: Int,
    ): GetChatMessagesResponse {
        val validatedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val pageable = PageRequest.of(0, validatedSize)

        log.info(
            "Chat messages read requested. roomUuid={}, requesterUuid={}, beforeMessageUuid={}, size={}",
            roomUuid,
            requesterUuid,
            beforeMessageUuid,
            validatedSize,
        )

        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        validateReadPermission(partyUuid, requesterUuid)

        val messages = if (beforeMessageUuid == null) {
            chatMessageRepository.findLatestMessages(
                roomUuid = roomUuid,
                pageable = pageable,
            )
        } else {
            val cursorMessage = chatMessageRepository.findByUuid(beforeMessageUuid)
                ?: throw ChatMessageNotFoundException(beforeMessageUuid.toString())

            if (cursorMessage.room.uuid != roomUuid) {
                throw ChatMessageNotFoundException(beforeMessageUuid.toString())
            }

            chatMessageRepository.findOlderMessages(
                roomUuid = roomUuid,
                cursorCreatedAt = cursorMessage.createdAt,
                cursorId = cursorMessage.id,
                pageable = pageable,
            )
        }

        val ascendingMessages = messages.asReversed()
        val messageResponses = ascendingMessages.map(ChatMessageResponse::of)

        val nextCursor = messages.lastOrNull()?.uuid

        log.info(
            "Chat messages read completed. roomUuid={}, requesterUuid={}, messageCount={}, nextCursor={}",
            roomUuid,
            requesterUuid,
            messageResponses.size,
            nextCursor,
        )

        return GetChatMessagesResponse(
            messages = messageResponses,
            nextCursor = nextCursor,
        )
    }

    @Transactional(readOnly = true)
    fun getUnreadCount(
        roomUuid: UUID,
        requesterUuid: UUID,
    ): Long {
        log.info(
            "Unread count requested. roomUuid={}, requesterUuid={}",
            roomUuid,
            requesterUuid,
        )

        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        validateReadPermission(partyUuid, requesterUuid)

        val participant = chatParticipantRepository.findByUser_UuidAndRoom_Uuid(
            userUuid = requesterUuid,
            roomUuid = roomUuid,
        )

        val unreadCount = if (participant?.lastReadAt == null) {
            chatMessageRepository.countByRoom_UuidAndSender_UuidNot(
                roomUuid = roomUuid,
                senderUuid = requesterUuid,
            )
        } else {
            chatMessageRepository.countByRoom_UuidAndCreatedAtAfterAndSender_UuidNot(
                roomUuid = roomUuid,
                createdAt = participant.lastReadAt!!,
                senderUuid = requesterUuid,
            )
        }

        log.info(
            "Unread count calculated. roomUuid={}, requesterUuid={}, unreadCount={}",
            roomUuid,
            requesterUuid,
            unreadCount,
        )

        return unreadCount
    }

    @Transactional(readOnly = true)
    fun getChatRooms(
        requesterUuid: UUID,
    ): GetChatRoomsResponse {
        log.info("Chat room list requested. requesterUuid={}", requesterUuid)

        val memberships = userPartyRepository.findAllWithPartyByUserUuidAndMemberStatus(
            userUuid = requesterUuid,
            memberStatus = MemberStatus.JOINED,
        )

        if (memberships.isEmpty()) {
            log.info("Chat room list completed with no joined rooms. requesterUuid={}", requesterUuid)
            return GetChatRoomsResponse(rooms = emptyList())
        }

        val partyByUuid = memberships.associate { membership ->
            membership.party.uuid to membership.party
        }

        val rooms = chatRoomRepository.findAllWithPartyByPartyUuidIn(partyByUuid.keys)
        val roomUuids = rooms.map { it.uuid }

        val latestMessageByRoomUuid = if (roomUuids.isEmpty()) {
            emptyMap()
        } else {
            chatMessageRepository.findLatestMessagesByRoomUuids(roomUuids)
                .associateBy { it.room.uuid }
        }

        val unreadCountByRoomUuid = buildUnreadCountMap(
            roomUuids = roomUuids,
            requesterUuid = requesterUuid,
        )

        val roomSummaries = rooms.map { room ->
            val latestMessage = latestMessageByRoomUuid[room.uuid]
            val unreadCount = unreadCountByRoomUuid[room.uuid] ?: 0L

            ChatRoomSummaryResponse(
                roomUuid = room.uuid,
                partyUuid = room.party.uuid,
                partyName = room.party.name,
                lastMessage = latestMessage?.body,
                lastMessageAt = latestMessage?.createdAt,
                unreadCount = unreadCount,
            )
        }.sortedWith(
            compareByDescending<ChatRoomSummaryResponse> { it.lastMessageAt }
                .thenByDescending { it.roomUuid.toString() }
        )

        log.info(
            "Chat room list completed. requesterUuid={}, roomCount={}",
            requesterUuid,
            roomSummaries.size,
        )

        return GetChatRoomsResponse(rooms = roomSummaries)
    }

    private fun buildUnreadCountMap(
        roomUuids: List<UUID>,
        requesterUuid: UUID,
    ): Map<UUID, Long> {
        if (roomUuids.isEmpty()) {
            return emptyMap()
        }

        val participants = roomUuids.associateWith { roomUuid ->
            chatParticipantRepository.findByUser_UuidAndRoom_Uuid(
                userUuid = requesterUuid,
                roomUuid = roomUuid,
            )
        }

        val roomUuidsWithoutLastReadAt = participants
            .filterValues { participant -> participant?.lastReadAt == null }
            .keys

        val unreadMap = mutableMapOf<UUID, Long>()

        if (roomUuidsWithoutLastReadAt.isNotEmpty()) {
            chatMessageRepository.countUnreadByRoomUuidsWithoutLastReadAt(
                roomUuids = roomUuidsWithoutLastReadAt,
                requesterUuid = requesterUuid,
            ).forEach { projection ->
                unreadMap[projection.getRoomUuid()] = projection.getUnreadCount()
            }
        }

        participants.forEach { (roomUuid, participant) ->
            val lastReadAt = participant?.lastReadAt ?: return@forEach

            val projection = chatMessageRepository.countUnreadByRoomUuidAfterLastReadAt(
                roomUuid = roomUuid,
                requesterUuid = requesterUuid,
                lastReadAt = lastReadAt,
            )

            unreadMap[roomUuid] = projection?.getUnreadCount() ?: 0L
        }

        return unreadMap
    }

    private fun validateReadPermission(
        partyUuid: UUID,
        requesterUuid: UUID,
    ) {
        val hasPermission = userPartyRepository.existsByParty_UuidAndUser_UuidAndMemberStatus(
            partyUuid = partyUuid,
            userUuid = requesterUuid,
            memberStatus = MemberStatus.JOINED,
        )

        if (!hasPermission) {
            log.warn(
                "User does not have permission to read chat data. partyUuid={}, requesterUuid={}",
                partyUuid,
                requesterUuid,
            )
            throw ChatRoomAccessDeniedException(requesterUuid.toString())
        }
    }

    companion object {
        private const val MAX_PAGE_SIZE = 30
    }
}
