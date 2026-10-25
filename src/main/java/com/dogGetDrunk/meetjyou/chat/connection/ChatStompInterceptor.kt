package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.auth.UnauthenticatedException
import com.dogGetDrunk.meetjyou.common.exception.business.chat.ChatRoomAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.ChatRoomNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.user.UserWithdrawnException
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component
import java.security.Principal
import java.util.UUID

@Component
class ChatStompInterceptor(
    private val jwtProvider: JwtProvider,
    private val chatRoomRepository: ChatRoomRepository,
    private val userPartyRepository: UserPartyRepository,
    private val userRepository: UserRepository,
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
        val roomUuid = accessor.getFirstNativeHeader(ROOM_UUID_HEADER)
            ?.let { rawRoomUuid -> runCatching { UUID.fromString(rawRoomUuid) }.getOrNull() }
            ?: throw InvalidInputException(value = ROOM_UUID_HEADER, message = "roomUuid header is missing or invalid")

        val userUuid = resolveUserUuidForConnect(accessor, roomUuid)
        validateActiveUser(userUuid, roomUuid)
        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        validateMembership(roomUuid, partyUuid, userUuid)

        accessor.sessionAttributes?.put("roomUuid", roomUuid)
        accessor.sessionAttributes?.put("userUuid", userUuid)
        accessor.user = ChatPrincipal(userUuid)

        log.info("STOMP CONNECT authorized. roomUuid={}, userUuid={}", roomUuid, userUuid)

        return MessageBuilder.createMessage(message.payload, accessor.messageHeaders)
    }

    private fun handleSubscribe(
        accessor: StompHeaderAccessor,
        message: Message<*>,
    ): Message<*> {
        val destination = accessor.destination
            ?: throw InvalidInputException(value = "destination", message = "STOMP destination is missing")

        if (!destination.startsWith(CHAT_SUBSCRIBE_PREFIX)) {
            return message
        }

        val roomUuid = extractRoomUuidFromDestination(destination)
            ?: throw InvalidInputException(value = destination, message = "Chat room UUID in destination is invalid")

        val userUuid = accessor.sessionAttributes?.get("userUuid") as? UUID
            ?: throw UnauthenticatedException(destination)

        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

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

        if (token.isNullOrBlank()) {
            log.warn("STOMP CONNECT rejected because Authorization header was missing. roomUuid={}", roomUuid)
            throw UnauthenticatedException(roomUuid.toString())
        }

        // Keep the JWT exception as is: an expired token (EXPIRED_TOKEN) tells the app to refresh
        // and reconnect, a forged one (INVALID_JWT) does not.
        return try {
            jwtProvider.validateAccessTokenOrThrow(token)
            jwtProvider.getUserUuid(token)
        } catch (e: CustomJwtException) {
            log.warn("STOMP CONNECT rejected: {}. roomUuid={}", e.errorCode, roomUuid)
            throw e
        }
    }

    private fun validateActiveUser(userUuid: UUID, roomUuid: UUID) {
        userRepository.findByUuid(userUuid)
            ?.takeIf { it.isActive() }
            ?: run {
                log.warn(
                    "STOMP CONNECT rejected because the user is withdrawn or unknown. roomUuid={}, userUuid={}",
                    roomUuid,
                    userUuid,
                )
                throw UserWithdrawnException(userUuid.toString(), message = "Withdrawn or unknown user on CONNECT")
            }
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
            throw ChatRoomAccessDeniedException(roomUuid.toString())
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
        private const val ROOM_UUID_HEADER = "roomUuid"
    }
}
