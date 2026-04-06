package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.dto.ChatRoomSummaryResponse
import com.dogGetDrunk.meetjyou.chat.dto.GetChatMessagesResponse
import com.dogGetDrunk.meetjyou.chat.dto.GetChatRoomsResponse
import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageResponse
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
import java.util.UUID

@Service
class ChatReadService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val userPartyRepository: UserPartyRepository,
    private val chatRoomEventBroadcaster: ChatRoomEventBroadcaster,
) {

    private val log = LoggerFactory.getLogger(ChatReadService::class.java)

    @Transactional
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
        val messageResponses = toChatMessageResponses(ascendingMessages)

        val nextCursor = messages.lastOrNull()?.uuid

        if (beforeMessageUuid == null) {
            ascendingMessages.lastOrNull()?.let { latestMessage ->
                updateLastReadPosition(
                    roomUuid = roomUuid,
                    requesterUuid = requesterUuid,
                    messageId = latestMessage.id,
                )
            }
        }

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

        val membership = userPartyRepository.findByParty_UuidAndUser_Uuid(
            partyUuid = partyUuid,
            userUuid = requesterUuid,
        ) ?: throw ChatRoomAccessDeniedException(requesterUuid.toString())

        val unreadCount = if (membership.lastReadMessageId == null) {
            chatMessageRepository.countByRoom_UuidAndSender_UuidNot(
                roomUuid = roomUuid,
                senderUuid = requesterUuid,
            )
        } else {
            chatMessageRepository.countByRoom_UuidAndIdGreaterThanAndSender_UuidNot(
                roomUuid = roomUuid,
                id = membership.lastReadMessageId!!,
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

        val memberships = userPartyRepository.findAllWithPartyByUserUuidAndMemberStatus(
            userUuid = requesterUuid,
            memberStatus = MemberStatus.JOINED,
        ).filter { it.party.uuid in roomUuids }
            .associateBy { it.party.uuid }

        val roomUuidsWithoutLastReadMessageId = memberships
            .filterValues { membership -> membership.lastReadMessageId == null }
            .keys

        val unreadMap = mutableMapOf<UUID, Long>()

        if (roomUuidsWithoutLastReadMessageId.isNotEmpty()) {
            chatMessageRepository.countUnreadByRoomUuidsWithoutLastReadMessageId(
                roomUuids = roomUuidsWithoutLastReadMessageId,
                requesterUuid = requesterUuid,
            ).forEach { projection ->
                unreadMap[projection.getRoomUuid()] = projection.getUnreadCount()
            }
        }

        memberships.forEach { (roomUuid, membership) ->
            val lastReadMessageId = membership.lastReadMessageId ?: return@forEach

            val projection = chatMessageRepository.countUnreadByRoomUuidAfterLastReadMessageId(
                roomUuid = roomUuid,
                requesterUuid = requesterUuid,
                lastReadMessageId = lastReadMessageId,
            )

            unreadMap[roomUuid] = projection?.getUnreadCount() ?: 0L
        }

        return unreadMap
    }

    private fun validateReadPermission(
        partyUuid: UUID,
        requesterUuid: UUID,
    ) {
        val membership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, requesterUuid)

        if (membership?.isActiveMember() != true) {
            log.warn(
                "User does not have permission to read chat data. partyUuid={}, requesterUuid={}",
                partyUuid,
                requesterUuid,
            )
            throw ChatRoomAccessDeniedException(requesterUuid.toString())
        }
    }

    @Transactional
    fun markLatestMessageAsRead(
        roomUuid: UUID,
        requesterUuid: UUID,
    ) {
        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        validateReadPermission(partyUuid, requesterUuid)

        val latestMessage = chatMessageRepository.findLatestMessagesWithSenderAndRoom(
            roomUuid = roomUuid,
            pageable = PageRequest.of(0, 1),
        ).firstOrNull() ?: return

        updateLastReadPosition(roomUuid, requesterUuid, latestMessage.id)
    }

    @Transactional
    fun markAsRead(
        roomUuid: UUID,
        requesterUuid: UUID,
        messageUuid: UUID?,
    ) {
        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        validateReadPermission(partyUuid, requesterUuid)

        val targetMessageId = if (messageUuid == null) {
            chatMessageRepository.findLatestMessagesWithSenderAndRoom(
                roomUuid = roomUuid,
                pageable = PageRequest.of(0, 1),
            ).firstOrNull()?.id
        } else {
            val message = chatMessageRepository.findWithSenderAndRoomByUuid(messageUuid)
                ?: throw ChatMessageNotFoundException(messageUuid.toString())

            if (message.room.uuid != roomUuid) {
                throw ChatMessageNotFoundException(messageUuid.toString())
            }

            message.id
        } ?: return

        updateLastReadPosition(roomUuid, requesterUuid, targetMessageId)
    }

    @Transactional
    fun markLatestMessageAsReadForUsers(
        roomUuid: UUID,
        userUuids: Set<UUID>,
        messageId: Long,
    ) {
        if (userUuids.isEmpty()) {
            return
        }

        userUuids.forEach { userUuid ->
            runCatching {
                updateLastReadPosition(roomUuid, userUuid, messageId)
            }.onFailure { throwable ->
                log.debug(
                    "Read position update skipped. roomUuid={}, userUuid={}, reason={}",
                    roomUuid,
                    userUuid,
                    throwable.message,
                )
            }
        }
    }

    private fun updateLastReadPosition(
        roomUuid: UUID,
        requesterUuid: UUID,
        messageId: Long,
    ) {
        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())
        val membership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, requesterUuid)
            ?: throw ChatRoomAccessDeniedException(requesterUuid.toString())

        if (!membership.isActiveMember()) {
            throw ChatRoomAccessDeniedException(requesterUuid.toString())
        }

        val previousLastReadMessageId = membership.lastReadMessageId
        membership.updateLastReadMessageId(messageId)

        if (membership.lastReadMessageId != null && membership.lastReadMessageId != previousLastReadMessageId) {
            chatRoomEventBroadcaster.broadcastChatReadUpdated(
                roomUuid = roomUuid,
                partyUuid = partyUuid,
                readerUserUuid = requesterUuid,
                previousLastReadMessageId = previousLastReadMessageId,
                currentLastReadMessageId = membership.lastReadMessageId!!,
            )
        }
    }

    private fun toChatMessageResponses(messages: List<com.dogGetDrunk.meetjyou.chat.message.ChatMessage>): List<ChatMessageResponse> {
        if (messages.isEmpty()) {
            return emptyList()
        }

        val unreadCountByMessageId = chatMessageRepository.countUnreadByMessageIds(messages.map { it.id })
            .associate { it.getMessageId() to it.getUnreadCount() }

        return messages.map { message ->
            ChatMessageResponse.of(
                chatMessage = message,
                unreadCount = unreadCountByMessageId[message.id] ?: 0L,
            )
        }
    }

    companion object {
        private const val MAX_PAGE_SIZE = 30
    }
}
