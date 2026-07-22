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

    // Every mutation of a room entry — inner-set edits included — runs inside a single
    // connectedSessions.compute(roomUuid) so the room map and its nested user map are updated
    // under one lock. This serializes connect/disconnect on the same room, closing two races
    // the previous check-then-act code had: (1) a concurrent connect adding a session between a
    // disconnect's isEmpty() check and its key removal, and (2) a connect writing into an inner
    // map that a disconnect had just pruned from the room (orphaned write). Reads below stay
    // lock-free, so nested maps remain ConcurrentHashMap / newKeySet for safe concurrent reads.
    fun connectUser(roomUuid: UUID, userUuid: UUID, sessionId: String) {
        connectedSessions.compute(roomUuid) { _, existingUserMap ->
            val userMap = existingUserMap ?: ConcurrentHashMap()
            val sessions = userMap[userUuid] ?: ConcurrentHashMap.newKeySet()
            sessions.add(sessionId)
            userMap[userUuid] = sessions
            userMap
        }

        log.debug("User session connected to chat room. roomUuid={}, userUuid={}, sessionId={}", roomUuid, userUuid, sessionId)
    }

    fun disconnectUser(roomUuid: UUID, userUuid: UUID, sessionId: String) {
        connectedSessions.compute(roomUuid) { _, userMap ->
            if (userMap == null) return@compute null
            val sessions = userMap[userUuid]
            sessions?.remove(sessionId)
            if (sessions.isNullOrEmpty()) userMap.remove(userUuid)
            if (userMap.isEmpty()) null else userMap
        }

        log.debug("User session disconnected from chat room. roomUuid={}, userUuid={}, sessionId={}", roomUuid, userUuid, sessionId)
    }

    fun disconnectAllSessions(roomUuid: UUID, userUuid: UUID) {
        connectedSessions.compute(roomUuid) { _, userMap ->
            if (userMap == null) return@compute null
            userMap.remove(userUuid)
            if (userMap.isEmpty()) null else userMap
        }

        log.debug("All sessions disconnected for user in chat room. roomUuid={}, userUuid={}", roomUuid, userUuid)
    }

    fun isUserConnected(roomUuid: UUID, userUuid: UUID): Boolean {
        return connectedSessions[roomUuid]?.get(userUuid)?.isNotEmpty() == true
    }

    fun getConnectedUsers(roomUuid: UUID): Set<UUID> {
        return connectedSessions[roomUuid]
            ?.filterValues { it.isNotEmpty() }
            ?.keys?.toSet()
            ?: emptySet()
    }
}
