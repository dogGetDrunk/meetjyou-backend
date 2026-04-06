package com.dogGetDrunk.meetjyou.chat.event

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ChatRoomEventBroadcaster(
    private val messagingTemplate: SimpMessagingTemplate,
) {

    fun broadcastPartyCompleted(
        roomUuid: UUID,
        partyUuid: UUID,
        actorUserUuid: UUID,
    ) {
        broadcast(
            ChatRoomEvent(
                type = ChatRoomEventType.PARTY_COMPLETED,
                roomUuid = roomUuid,
                partyUuid = partyUuid,
                actorUserUuid = actorUserUuid,
            )
        )
    }

    fun broadcastMemberBanned(
        roomUuid: UUID,
        partyUuid: UUID,
        actorUserUuid: UUID,
        targetUserUuid: UUID,
    ) {
        broadcast(
            ChatRoomEvent(
                type = ChatRoomEventType.MEMBER_BANNED,
                roomUuid = roomUuid,
                partyUuid = partyUuid,
                actorUserUuid = actorUserUuid,
                targetUserUuid = targetUserUuid,
            )
        )
    }

    fun broadcastMemberLeft(
        roomUuid: UUID,
        partyUuid: UUID,
        targetUserUuid: UUID,
    ) {
        broadcast(
            ChatRoomEvent(
                type = ChatRoomEventType.MEMBER_LEFT,
                roomUuid = roomUuid,
                partyUuid = partyUuid,
                actorUserUuid = targetUserUuid,
                targetUserUuid = targetUserUuid,
            )
        )
    }

    fun broadcastChatReadUpdated(
        roomUuid: UUID,
        partyUuid: UUID,
        readerUserUuid: UUID,
        previousLastReadMessageId: Long?,
        currentLastReadMessageId: Long,
    ) {
        broadcast(
            ChatRoomEvent(
                type = ChatRoomEventType.CHAT_READ_UPDATED,
                roomUuid = roomUuid,
                partyUuid = partyUuid,
                readerUserUuid = readerUserUuid,
                previousLastReadMessageId = previousLastReadMessageId,
                currentLastReadMessageId = currentLastReadMessageId,
            )
        )
    }

    private fun broadcast(event: ChatRoomEvent) {
        messagingTemplate.convertAndSend("/sub/chat/room/${event.roomUuid}/events", event)
    }
}
