package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.ChatController
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

        // STOMP is the STOMP 1.2 alias of CONNECT; letting it fall through would open a connection
        // without any token check.
        return when (command) {
            StompCommand.CONNECT, StompCommand.STOMP -> handleConnect(accessor, message)
            StompCommand.SUBSCRIBE -> handleSubscribe(accessor, message)
            StompCommand.SEND -> handleSend(accessor, message)
            else -> message
        }
    }

    private fun handleConnect(
        accessor: StompHeaderAccessor,
        message: Message<*>,
    ): Message<*> {
        rejectIfAlreadyConnected(accessor)

        val roomUuid = accessor.getFirstNativeHeader(ROOM_UUID_HEADER)
            ?.let { rawRoomUuid -> runCatching { UUID.fromString(rawRoomUuid) }.getOrNull() }
            ?: throw InvalidInputException(value = ROOM_UUID_HEADER, message = "roomUuid header is missing or invalid")

        val userUuid = resolveUserUuidForConnect(accessor, roomUuid)
        validateActiveUser(userUuid, roomUuid)
        val partyUuid = chatRoomRepository.findPartyUuidByRoomUuid(roomUuid)
            ?: throw ChatRoomNotFoundException(roomUuid.toString())

        validateMembership(roomUuid, partyUuid, userUuid)

        accessor.sessionAttributes?.put(ChatSessionAttributes.ROOM_UUID, roomUuid)
        accessor.sessionAttributes?.put(ChatSessionAttributes.USER_UUID, userUuid)
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

        if (destination == ERROR_QUEUE_SUBSCRIPTION) {
            return message
        }

        val roomUuid = extractRoomUuidFromDestination(destination)
            ?: throw InvalidInputException(value = destination, message = "Unsupported subscription destination")

        val userUuid = accessor.sessionAttributes?.get(ChatSessionAttributes.USER_UUID) as? UUID
            ?: throw UnauthenticatedException(destination)

        validateConnectedRoom(accessor, roomUuid, userUuid)

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

    // Spring passes a second CONNECT on the same session through to here (#295). Rebinding the
    // connection to another room would leave it open, still subscribed, after a kick from the first.
    private fun rejectIfAlreadyConnected(accessor: StompHeaderAccessor) {
        val boundRoomUuid = accessor.sessionAttributes?.get(ChatSessionAttributes.ROOM_UUID) ?: return
        log.warn(
            "STOMP CONNECT rejected because the connection is already bound to a room. boundRoomUuid={}, sessionId={}",
            boundRoomUuid,
            accessor.sessionId,
        )
        throw InvalidInputException(value = ROOM_UUID_HEADER, message = "Connection is already bound to a room")
    }

    // Only application destinations reach @MessageMapping and ChatService's membership check. The
    // simple broker also takes client SENDs to its own prefixes (/sub, /queue, /user) and hands them
    // straight to every subscriber, so those would let anyone inject frames into another room (#294).
    private fun handleSend(
        accessor: StompHeaderAccessor,
        message: Message<*>,
    ): Message<*> {
        accessor.destination
            ?.takeIf { it.startsWith(SEND_DESTINATION_PREFIX) }
            ?: run {
                log.warn(
                    "STOMP SEND rejected for a non-application destination. destination={}, sessionId={}",
                    accessor.destination,
                    accessor.sessionId,
                )
                throw InvalidInputException(
                    value = accessor.destination ?: "destination",
                    message = "Unsupported send destination",
                )
            }
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

    // A connection is bound to the room it was authorized for on CONNECT; closing sessions on removal
    // relies on that, so a session subscribed to another room would survive a kick from it.
    private fun validateConnectedRoom(accessor: StompHeaderAccessor, roomUuid: UUID, userUuid: UUID) {
        val connectedRoomUuid = accessor.sessionAttributes?.get(ChatSessionAttributes.ROOM_UUID) as? UUID
        if (connectedRoomUuid != roomUuid) {
            log.warn(
                "STOMP SUBSCRIBE rejected for another room. roomUuid={}, connectedRoomUuid={}, userUuid={}",
                roomUuid,
                connectedRoomUuid,
                userUuid,
            )
            throw ChatRoomAccessDeniedException(roomUuid.toString())
        }
    }

    /**
     * Allowlist: only `/sub/chat/room/{uuid}` and `/sub/chat/room/{uuid}/events` pass. The simple broker
     * treats a subscription destination as an Ant pattern, so anything looser (a `**` wildcard) would
     * receive every room's broadcasts. The UUID must be in canonical form: a broadcast destination is
     * matched as an exact string.
     */
    private fun extractRoomUuidFromDestination(destination: String): UUID? {
        val match = ROOM_SUBSCRIPTION_PATTERN.matchEntire(destination) ?: return null
        val rawRoomUuid = match.groupValues[1]
        return runCatching { UUID.fromString(rawRoomUuid) }.getOrNull()
            ?.takeIf { it.toString() == rawRoomUuid }
    }

    private class ChatPrincipal(
        private val userUuid: UUID,
    ) : Principal {
        override fun getName(): String = userUuid.toString()
    }

    companion object {
        private val ROOM_SUBSCRIPTION_PATTERN = Regex("^/sub/chat/room/([^/]+)(/events)?$")
        private const val ERROR_QUEUE_SUBSCRIPTION = "/user${ChatController.ERROR_QUEUE}"
        private const val ROOM_UUID_HEADER = "roomUuid"
        const val APP_DESTINATION_PREFIX = "/pub"
        private const val SEND_DESTINATION_PREFIX = "$APP_DESTINATION_PREFIX/"
    }
}
