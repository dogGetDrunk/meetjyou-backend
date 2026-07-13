package com.dogGetDrunk.meetjyou.chat.connection

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks presence per WebSocket session, not per user: a user connected from multiple devices
 * (e.g. web + mobile) must stay "connected" as long as any one of those sessions is still open.
 */
@Component
class ChatSessionTracker {

    private val log = LoggerFactory.getLogger(ChatSessionTracker::class.java)

    private val connectedSessions: MutableMap<UUID, MutableMap<UUID, MutableSet<String>>> = ConcurrentHashMap()

    fun connectUser(roomUuid: UUID, userUuid: UUID, sessionId: String) {
        connectedSessions.computeIfAbsent(roomUuid) { ConcurrentHashMap() }
            .computeIfAbsent(userUuid) { ConcurrentHashMap.newKeySet() }
            .add(sessionId)

        log.debug("User session connected to chat room. roomUuid={}, userUuid={}, sessionId={}", roomUuid, userUuid, sessionId)
    }

    fun disconnectUser(roomUuid: UUID, userUuid: UUID, sessionId: String) {
        val userSessions = connectedSessions[roomUuid] ?: return
        userSessions[userUuid]?.remove(sessionId)

        if (userSessions[userUuid]?.isEmpty() == true) {
            userSessions.remove(userUuid)
        }
        if (userSessions.isEmpty()) {
            connectedSessions.remove(roomUuid)
        }

        log.debug("User session disconnected from chat room. roomUuid={}, userUuid={}, sessionId={}", roomUuid, userUuid, sessionId)
    }

    fun disconnectAllSessions(roomUuid: UUID, userUuid: UUID) {
        val userSessions = connectedSessions[roomUuid] ?: return
        userSessions.remove(userUuid)

        if (userSessions.isEmpty()) {
            connectedSessions.remove(roomUuid)
        }

        log.debug("All sessions disconnected for user in chat room. roomUuid={}, userUuid={}", roomUuid, userUuid)
    }

    fun isUserConnected(roomUuid: UUID, userUuid: UUID): Boolean {
        return connectedSessions[roomUuid]?.get(userUuid)?.isNotEmpty() == true
    }

    fun getConnectedUsers(roomUuid: UUID): Set<UUID> {
        return connectedSessions[roomUuid]?.keys?.toSet() ?: emptySet()
    }
}
