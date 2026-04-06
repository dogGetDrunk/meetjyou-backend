package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Component
import java.security.Principal
import java.util.UUID

@Component
class ChatStompInterceptor(
    private val jwtProvider: JwtProvider,
    private val chatRoomRepository: ChatRoomRepository,
    private val userPartyRepository: UserPartyRepository,
) : ChannelInterceptor {

    private val log = LoggerFactory.getLogger(ChatStompInterceptor::class.java)

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = StompHeaderAccessor.wrap(message)
        val command = accessor.command ?: return message

        return when (command) {
            StompCommand.CONNECT -> handleConnect(accessor, message)
            StompCommand.SUBSCRIBE -> handleSubscribe(accessor, message)
            else -> message
        }
    }

    private fun handleConnect(
        accessor: StompHeaderAccessor,
        message: Message<*>,
    ): Message<*> {
        val roomUuid = accessor.getFirstNativeHeader("roomUuid")
            ?.let { rawRoomUuid -> runCatching { UUID.fromString(rawRoomUuid) }.getOrNull() }
            ?: throw IllegalArgumentException("roomUuid header is missing or invalid.")

        val userUuid = resolveUserUuidForConnect(accessor, roomUuid)
        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw IllegalArgumentException("Chat room was not found.")

        validateMembership(roomUuid, partyUuid, userUuid)

        accessor.sessionAttributes?.put("roomUuid", roomUuid)
        accessor.sessionAttributes?.put("userUuid", userUuid)
        accessor.user = ChatPrincipal(userUuid)

        log.info("STOMP CONNECT authorized. roomUuid={}, userUuid={}", roomUuid, userUuid)

        return message
    }

    private fun handleSubscribe(
        accessor: StompHeaderAccessor,
        message: Message<*>,
    ): Message<*> {
        val destination = accessor.destination
            ?: throw IllegalArgumentException("STOMP destination is missing.")

        if (!destination.startsWith(CHAT_SUBSCRIBE_PREFIX)) {
            return message
        }

        val roomUuid = extractRoomUuidFromDestination(destination)
            ?: throw IllegalArgumentException("Chat room UUID in destination is invalid.")

        val userUuid = accessor.sessionAttributes?.get("userUuid") as? UUID
            ?: throw IllegalArgumentException("Authenticated chat session is required for subscription.")

        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw IllegalArgumentException("Chat room was not found.")

        validateMembership(roomUuid, partyUuid, userUuid)

        log.info(
            "STOMP SUBSCRIBE authorized. destination={}, roomUuid={}, userUuid={}",
            destination,
            roomUuid,
            userUuid,
        )

        return message
    }

    private fun resolveUserUuidForConnect(
        accessor: StompHeaderAccessor,
        roomUuid: UUID,
    ): UUID {
        val token = accessor.getFirstNativeHeader("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        if (!token.isNullOrBlank()) {
            return runCatching { jwtProvider.getUserUuid(token) }
                .getOrElse {
                    log.warn("STOMP CONNECT rejected because JWT parsing failed. roomUuid={}", roomUuid)
                    throw IllegalArgumentException("JWT parsing failed.")
                }
        }

        log.warn("STOMP CONNECT rejected because Authorization header was missing. roomUuid={}", roomUuid)
        throw IllegalArgumentException("Authentication information is missing.")
    }

    private fun validateMembership(
        roomUuid: UUID,
        partyUuid: UUID,
        userUuid: UUID,
    ) {
        val membership = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)

        if (membership?.isActiveMember() != true) {
            log.warn(
                "Chat access denied because user does not belong to the party. roomUuid={}, partyUuid={}, userUuid={}",
                roomUuid,
                partyUuid,
                userUuid,
            )
            throw IllegalArgumentException("User does not have permission to access this chat room.")
        }
    }

    private fun extractRoomUuidFromDestination(destination: String): UUID? {
        val roomUuid = destination.removePrefix(CHAT_SUBSCRIBE_PREFIX).substringBefore("/")
        return runCatching { UUID.fromString(roomUuid) }.getOrNull()
    }

    private class ChatPrincipal(
        private val userUuid: UUID,
    ) : Principal {
        override fun getName(): String = userUuid.toString()
    }

    companion object {
        private const val CHAT_SUBSCRIBE_PREFIX = "/sub/chat/room/"
    }
}
