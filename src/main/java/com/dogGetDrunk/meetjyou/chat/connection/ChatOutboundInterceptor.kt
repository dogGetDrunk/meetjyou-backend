package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ChatOutboundInterceptor(
    private val chatRoomRepository: ChatRoomRepository,
    private val userPartyRepository: UserPartyRepository,
) : ChannelInterceptor {

    private val log = LoggerFactory.getLogger(ChatOutboundInterceptor::class.java)

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = StompHeaderAccessor.wrap(message)
        val destination = accessor.destination ?: return message

        if (!destination.startsWith(CHAT_SUBSCRIBE_PREFIX)) {
            return message
        }

        val roomUuid = extractRoomUuid(destination) ?: return message
        val userUuid = resolveUserUuid(accessor) ?: return message
        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid) ?: return null
        val membership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)

        if (membership?.isActiveMember() == true) {
            return message
        }

        log.info(
            "Outbound chat message blocked for inactive member. roomUuid={}, partyUuid={}, userUuid={}",
            roomUuid,
            partyUuid,
            userUuid,
        )
        return null
    }

    private fun resolveUserUuid(accessor: StompHeaderAccessor): UUID? {
        accessor.user?.name?.let { rawUserUuid ->
            runCatching { UUID.fromString(rawUserUuid) }.getOrNull()?.let { return it }
        }

        return accessor.sessionAttributes?.get("userUuid") as? UUID
    }

    private fun extractRoomUuid(destination: String): UUID? {
        val roomUuid = destination.removePrefix(CHAT_SUBSCRIBE_PREFIX).substringBefore("/")
        return runCatching { UUID.fromString(roomUuid) }.getOrNull()
    }

    companion object {
        private const val CHAT_SUBSCRIBE_PREFIX = "/sub/chat/room/"
    }
}
