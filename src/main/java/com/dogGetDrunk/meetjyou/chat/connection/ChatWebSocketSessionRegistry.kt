package com.dogGetDrunk.meetjyou.chat.connection

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.WebSocketHandlerDecorator
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds every open chat WebSocket so the server can close the ones a user is no longer allowed to
 * keep (kicked, left, withdrawn). Without this, access checks only run on CONNECT / SUBSCRIBE and an
 * already-open session keeps receiving the room's broadcasts until the client reconnects.
 *
 * Sessions are matched on the attributes [ChatStompInterceptor] sets on CONNECT (the STOMP session
 * attributes are the WebSocket session's attribute map). One connection serves one room.
 */
@Component
class ChatWebSocketSessionRegistry : WebSocketHandlerDecoratorFactory {

    private val log = LoggerFactory.getLogger(ChatWebSocketSessionRegistry::class.java)

    private val sessions: MutableMap<String, WebSocketSession> = ConcurrentHashMap()

    override fun decorate(handler: WebSocketHandler): WebSocketHandler =
        object : WebSocketHandlerDecorator(handler) {
            override fun afterConnectionEstablished(session: WebSocketSession) {
                sessions[session.id] = session
                super.afterConnectionEstablished(session)
            }

            override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
                sessions.remove(session.id)
                super.afterConnectionClosed(session, closeStatus)
            }
        }

    /** Closes [userUuid]'s sessions bound to [roomUuid], or to every room when [roomUuid] is null. */
    fun closeSessions(userUuid: UUID, roomUuid: UUID?, reason: String) {
        // Linear scan over open sessions: revocations are rare and the session count is small.
        sessions.values
            .filter { it.belongsTo(userUuid, roomUuid) }
            .forEach { close(it, userUuid, reason) }
    }

    private fun WebSocketSession.belongsTo(userUuid: UUID, roomUuid: UUID?): Boolean {
        if (attributes[ChatSessionAttributes.USER_UUID] != userUuid) return false
        return roomUuid == null || attributes[ChatSessionAttributes.ROOM_UUID] == roomUuid
    }

    private fun close(session: WebSocketSession, userUuid: UUID, reason: String) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(reason))
            log.info(
                "Chat session closed by the server. sessionId={}, userUuid={}, reason={}",
                session.id,
                userUuid,
                reason,
            )
        } catch (e: IOException) {
            log.warn("Failed to close chat session. sessionId={}, userUuid={}", session.id, userUuid, e)
        }
    }
}
